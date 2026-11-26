package com.example.studentprojecttracker.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.studentprojecttracker.R;
import com.example.studentprojecttracker.models.FileModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

    private List<FileModel> fileList;
    private OnFileOpenListener openListener;

    /** Uploaded files are opened by the fragment (they must be downloaded first). */
    public interface OnFileOpenListener {
        void onOpen(FileModel file);
    }

    public FileAdapter(List<FileModel> fileList) {
        this.fileList = fileList;
    }

    public FileAdapter(List<FileModel> fileList, OnFileOpenListener openListener) {
        this.fileList = fileList;
        this.openListener = openListener;
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileModel file = fileList.get(position);

        holder.tvFileName.setText(file.getName());

        // Format upload date
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        String dateStr = sdf.format(new Date(file.getUploadedAt()));
        String who = file.getUploaderName() != null ? file.getUploaderName()
                : (file.getUploadedBy() != null ? file.getUploadedBy() : "a member");
        // Section 4.3.4: show writes the server has not confirmed yet
        String what = file.isUpload()
                ? "Uploaded by " + who + " · " + formatSize(file.getSizeBytes())
                : "Link shared by " + who;
        holder.tvFileInfo.setText(file.isPending()
                ? "Saving… (waiting for connection)"
                : what + " · " + dateStr);

        // Uploaded files are downloaded by the fragment; links open in the browser
        View.OnClickListener open = v -> {
            if (file.isUpload() && openListener != null) {
                openListener.onOpen(file);
            } else {
                openFile(v.getContext(), file.getUrl());
            }
        };
        holder.itemView.setOnClickListener(open);
        holder.btnDownload.setOnClickListener(open);
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        if (bytes >= 1024) return (bytes / 1024) + " KB";
        return bytes + " B";
    }

    private void openFile(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Unable to open file", Toast.LENGTH_SHORT).show();
        }
    }

    public static class FileViewHolder extends RecyclerView.ViewHolder {
        ImageView imgFileIcon;
        TextView tvFileName;
        TextView tvFileInfo;
        ImageView btnDownload;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            imgFileIcon = itemView.findViewById(R.id.imgFileIcon);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFileInfo = itemView.findViewById(R.id.tvFileInfo);
            btnDownload = itemView.findViewById(R.id.btnDownload);
        }
    }
}