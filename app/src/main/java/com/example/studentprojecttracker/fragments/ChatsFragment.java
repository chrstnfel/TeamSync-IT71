package com.example.studentprojecttracker.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.studentprojecttracker.R;
import com.example.studentprojecttracker.activities.MainActivity;
import com.example.studentprojecttracker.adapters.ChatAdapter;
import com.example.studentprojecttracker.models.ChatMessage;
import com.example.studentprojecttracker.models.Project;
import com.example.studentprojecttracker.utils.DataContract;
import com.example.studentprojecttracker.utils.FirestoreHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * M4 Project Chat (FR-08): project-level messages in projects/{projectId}/messages,
 * updated in real time. Each message is saved with its activity log entry.
 */
public class ChatsFragment extends Fragment {

    private RecyclerView chatRecyclerView;
    private EditText messageInput;
    private ImageView attachIcon, sendIcon;

    private ChatAdapter chatAdapter;
    private final List<ChatMessage> chatList = new ArrayList<>();

    private FirestoreHelper firestoreHelper;
    private ListenerRegistration messagesListener;
    private String projectId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chats, container, false);

        firestoreHelper = new FirestoreHelper();

        // Get project from MainActivity
        Project currentProject = ((MainActivity) requireActivity()).getCurrentProject();
        projectId = currentProject.getProjectId();

        initializeViews(view);
        setupRecyclerView();
        listenForMessages();
        setupSend();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Detach the listener when the chat is not shown (Section 4.3.5)
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
    }

    private void initializeViews(View view) {
        chatRecyclerView = view.findViewById(R.id.chatRecyclerView);
        messageInput = view.findViewById(R.id.messageInput);
        attachIcon = view.findViewById(R.id.attachIcon);
        sendIcon = view.findViewById(R.id.sendIcon);
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(chatList);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        chatRecyclerView.setAdapter(chatAdapter);
    }

    private void setupSend() {
        sendIcon.setOnClickListener(v -> {
            String msg = messageInput.getText().toString().trim();
            if (TextUtils.isEmpty(msg)) return;
            if (msg.length() > DataContract.MSG_MAX_LENGTH) {
                Toast.makeText(getContext(), "Message is too long", Toast.LENGTH_SHORT).show();
                return;
            }
            sendMessage(msg);
        });
    }

    private void sendMessage(String text) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String senderName = user != null && user.getDisplayName() != null
                ? user.getDisplayName() : "Member";

        messageInput.setText("");
        firestoreHelper.sendMessage(projectId, senderName, text, (success, message) -> {
            if (!isAdded() || getContext() == null) return;
            if (!success) {
                // Nothing was saved: give the text back so the user can retry
                messageInput.setText(text);
                Toast.makeText(getContext(), "Message not sent: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void listenForMessages() {
        messagesListener = firestoreHelper.listenMessages(projectId, new FirestoreHelper.MessageCallback() {
            @Override
            public void onSuccess(List<ChatMessage> messages) {
                if (!isAdded()) return;
                chatList.clear();
                chatList.addAll(messages);
                chatAdapter.notifyDataSetChanged();
                if (!chatList.isEmpty()) {
                    chatRecyclerView.scrollToPosition(chatList.size() - 1);
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded() || getContext() == null) return;
                Toast.makeText(getContext(), "Chat error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
