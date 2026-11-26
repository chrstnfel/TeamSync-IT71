package com.example.studentprojecttracker.adapters;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.studentprojecttracker.R;
import com.example.studentprojecttracker.models.ChatMessage;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private List<ChatMessage> messages;
    private Context context;
    private String currentUserId;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
        // senderId is the sender's email (data contract), so compare emails
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? com.example.studentprojecttracker.utils.DataContract.normalizeEmail(
                        FirebaseAuth.getInstance().getCurrentUser().getEmail())
                : null;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // The context was never set before (it assigned itself), so use the parent's
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        holder.textMessage.setText(msg.getText());

        boolean isSender = msg.getSenderId() != null && msg.getSenderId().equals(currentUserId);

        // The container fills the row, so align the bubble with the container's
        // own gravity (layout_gravity has no effect on a full-width view).
        if (isSender) {
            holder.messageContainer.setGravity(Gravity.END);
            holder.textMessage.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_chat_sender));
        } else {
            holder.messageContainer.setGravity(Gravity.START);
            holder.textMessage.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_chat_receiver));
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        LinearLayout messageContainer;
        TextView textMessage;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            messageContainer = itemView.findViewById(R.id.messageContainer);
            textMessage = itemView.findViewById(R.id.textMessage);
        }
    }
}
