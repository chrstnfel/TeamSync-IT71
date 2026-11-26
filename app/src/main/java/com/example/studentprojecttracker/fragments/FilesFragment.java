package com.example.studentprojecttracker.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.studentprojecttracker.R;
import com.example.studentprojecttracker.activities.MainActivity;
import com.example.studentprojecttracker.adapters.FileAdapter;
import com.example.studentprojecttracker.models.FileModel;
import com.example.studentprojecttracker.models.Project;
import com.example.studentprojecttracker.utils.DataContract;
import com.example.studentprojecttracker.utils.FirestoreHelper;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * S7 Files (M5). Cloud Storage for Firebase needs the billing-enabled Blaze
 * plan, so files up to 5 MB are uploaded into Firestore (split into chunks),
 * and bigger files are shared as links (for example Google Drive). Each file
 * is saved with its activity log entry in one batch and appears on Android
 * and web in real time.
 */
public class FilesFragment extends Fragment {

    private static final String TAG = "FilesFragment";

    private RecyclerView filesRecyclerView;
    private MaterialButton shareButton;
    private FileAdapter fileAdapter;
    private final List<FileModel> fileList = new ArrayList<>();
    private Project currentProject;
    private FirestoreHelper firestoreHelper;
    private ListenerRegistration filesListener;

    private MaterialButton uploadButton;
    private androidx.activity.result.ActivityResultLauncher<String[]> filePicker;
    private final java.util.concurrent.ExecutorService io =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        firestoreHelper = new FirestoreHelper();
        if (getActivity() instanceof MainActivity) {
            currentProject = ((MainActivity) getActivity()).getCurrentProject();
        }
        // System file picker (no storage permission needed)
        filePicker = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                uri -> { if (uri != null) uploadPickedFile(uri); });
    }

    // ---------- T7 Upload (stored in Firestore chunks, 5 MB max) ----------

    private void uploadPickedFile(android.net.Uri uri) {
        if (currentProject == null || getContext() == null) return;
        android.content.ContentResolver resolver = requireContext().getContentResolver();

        // Name and size from the picker, so a large file is refused before reading it
        String name = "file";
        long size = -1;
        try (android.database.Cursor c = resolver.query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int n = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                int s = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (n >= 0) name = c.getString(n);
                if (s >= 0 && !c.isNull(s)) size = c.getLong(s);
            }
        }
        // Section 4.3.4: a file that is too large is rejected before anything is sent
        if (size > DataContract.MAX_UPLOAD_BYTES) {
            showTooLarge();
            return;
        }
        if (name.length() > DataContract.FILE_NAME_MAX) {
            name = name.substring(0, DataContract.FILE_NAME_MAX);
        }

        String mime = resolver.getType(uri);
        String fileName = name;
        uploadButton.setEnabled(false);
        Toast.makeText(getContext(), "Uploading " + fileName + "…", Toast.LENGTH_SHORT).show();

        io.execute(() -> {
            byte[] bytes;
            try (java.io.InputStream in = resolver.openInputStream(uri)) {
                bytes = readUpTo(in, DataContract.MAX_UPLOAD_BYTES + 1);
            } catch (Exception e) {
                postToUi(() -> {
                    uploadButton.setEnabled(true);
                    Toast.makeText(getContext(), "Could not read the file", Toast.LENGTH_SHORT).show();
                });
                return;
            }
            postToUi(() -> {
                if (bytes.length > DataContract.MAX_UPLOAD_BYTES) {
                    uploadButton.setEnabled(true);
                    showTooLarge();
                    return;
                }
                firestoreHelper.uploadFile(currentProject.getProjectId(), fileName, mime, bytes,
                        myName(), (success, message) -> {
                            if (!isAdded() || getContext() == null) return;
                            uploadButton.setEnabled(true);
                            Toast.makeText(getContext(),
                                    success ? "File uploaded" : "Not uploaded: " + message,
                                    Toast.LENGTH_SHORT).show();
                        });
            });
        });
    }

    private void showTooLarge() {
        new AlertDialog.Builder(requireContext())
                .setTitle("File is too large")
                .setMessage("Uploaded files can be up to 5 MB. For bigger files, upload them to "
                        + "Google Drive and use Share link instead.")
                .setPositiveButton("OK", null)
                .show();
    }

    private static byte[] readUpTo(java.io.InputStream in, int max) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            if (out.size() > max) break;
        }
        return out.toByteArray();
    }

    // ---------- Open an uploaded file ----------

    private void openUploadedFile(FileModel file) {
        if (currentProject == null || getContext() == null) return;
        Toast.makeText(getContext(), "Opening " + file.getName() + "…", Toast.LENGTH_SHORT).show();
        firestoreHelper.downloadFile(currentProject.getProjectId(), file, new FirestoreHelper.BytesCallback() {
            @Override
            public void onSuccess(byte[] bytes) {
                if (!isAdded()) return;
                io.execute(() -> {
                    try {
                        java.io.File dir = new java.io.File(requireContext().getCacheDir(), "shared_files");
                        if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("No cache dir");
                        // Keep only safe characters in the local file name
                        String safe = file.getName().replaceAll("[^A-Za-z0-9._ -]", "_");
                        java.io.File out = new java.io.File(dir, file.getFileId() + "_" + safe);
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                            fos.write(bytes);
                        }
                        postToUi(() -> viewFile(out, file.getMimeType()));
                    } catch (Exception e) {
                        postToUi(() -> Toast.makeText(getContext(), "Could not open the file",
                                Toast.LENGTH_SHORT).show());
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded() || getContext() == null) return;
                Toast.makeText(getContext(), "Could not download: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void viewFile(java.io.File file, String mimeType) {
        if (!isAdded() || getContext() == null) return;
        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(requireContext(),
                requireContext().getPackageName() + ".fileprovider", file);
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType != null ? mimeType : "*/*");
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(android.content.Intent.createChooser(intent, "Open with"));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(getContext(), "No app on this phone can open this file type",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void postToUi(Runnable r) {
        if (getActivity() != null) getActivity().runOnUiThread(() -> { if (isAdded()) r.run(); });
    }

    private String myName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null && user.getDisplayName() != null ? user.getDisplayName() : "Member";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_files, container, false);

        filesRecyclerView = view.findViewById(R.id.filesRecyclerView);
        shareButton = view.findViewById(R.id.shareLinkButton);
        shareButton.setOnClickListener(v -> showShareDialog());
        uploadButton = view.findViewById(R.id.uploadButton);
        uploadButton.setOnClickListener(v -> filePicker.launch(new String[]{"*/*"}));

        fileAdapter = new FileAdapter(fileList, this::openUploadedFile);
        filesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        filesRecyclerView.setAdapter(fileAdapter);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        listenForFiles();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (filesListener != null) {
            filesListener.remove();
            filesListener = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    private void listenForFiles() {
        if (currentProject == null) {
            Log.e(TAG, "No current project");
            return;
        }
        filesListener = firestoreHelper.getProjectFiles(currentProject.getProjectId(),
                new FirestoreHelper.FileCallback() {
                    @Override
                    public void onSuccess(List<FileModel> files) {
                        if (!isAdded()) return;
                        fileList.clear();
                        fileList.addAll(files);
                        fileAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded() || getContext() == null) return;
                        Toast.makeText(getContext(), "Error loading files: " + error,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** T7: ask for a file name and its share link, check them, then save + log. */
    private void showShareDialog() {
        if (currentProject == null || getContext() == null) return;

        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(pad, pad / 2, pad, 0);

        EditText etName = new EditText(getContext());
        etName.setHint("File name (e.g. Chapter 1 draft)");
        etName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        form.addView(etName);

        EditText etUrl = new EditText(getContext());
        etUrl.setHint("Share link (https://…)");
        etUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        form.addView(etUrl);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Share a file")
                .setMessage("Upload the file to Google Drive (or similar), set it to "
                        + "\"Anyone with the link\" or share it with your group, then paste the link.")
                .setView(form)
                .setPositiveButton("Share", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String url = etUrl.getText().toString().trim();

            // Section 4.3.4: invalid input is stopped on the client
            if (name.isEmpty() || name.length() > DataContract.FILE_NAME_MAX) {
                etName.setError("Enter a file name (up to " + DataContract.FILE_NAME_MAX + " characters)");
                return;
            }
            if (!url.startsWith("https://") || !Patterns.WEB_URL.matcher(url).matches()
                    || url.length() > DataContract.FILE_URL_MAX) {
                etUrl.setError("Enter a valid link starting with https://");
                return;
            }

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            String uploaderName = user != null && user.getDisplayName() != null
                    ? user.getDisplayName() : "Member";

            dialog.dismiss();
            firestoreHelper.shareFileLink(currentProject.getProjectId(), name, url, uploaderName,
                    (success, message) -> {
                        if (!isAdded() || getContext() == null) return;
                        Toast.makeText(getContext(), success ? "File shared" : "Not shared: " + message,
                                Toast.LENGTH_SHORT).show();
                    });
        }));

        dialog.show();
    }
}
