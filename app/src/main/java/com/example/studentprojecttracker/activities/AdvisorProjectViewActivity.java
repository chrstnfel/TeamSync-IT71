package com.example.studentprojecttracker.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.studentprojecttracker.R;
import com.example.studentprojecttracker.adapters.MemberProgressAdapter;
import com.example.studentprojecttracker.models.Comment;
import com.example.studentprojecttracker.models.MemberProgress;
import com.example.studentprojecttracker.models.Project;
import com.example.studentprojecttracker.models.Task;
import com.example.studentprojecttracker.models.User;
import com.example.studentprojecttracker.utils.CommentViews;
import com.example.studentprojecttracker.utils.DataContract;
import com.example.studentprojecttracker.utils.FirestoreHelper;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * S9 Teacher View (M7, FR-11): the invited teacher sees the overall progress
 * and each member's progress, computed live from the project's tasks, and
 * posts comments/feedback. Teachers cannot create or change tasks.
 */
public class AdvisorProjectViewActivity extends AppCompatActivity {

    private TextView tvProjectTitle, tvOverallProgress, tvTotalTasks, tvCompletedTasks, tvTotalMembers;
    private TextView tvNoFeedback;
    private ProgressBar progressOverall;
    private RecyclerView rvMemberProgress;
    private ImageView btnBack;
    private EditText etFeedback;
    private MaterialButton btnPostFeedback;
    private LinearLayout commentsContainer;

    private MemberProgressAdapter memberProgressAdapter;
    private final List<MemberProgress> memberProgressList = new ArrayList<>();
    private final Map<String, String> nameByEmail = new HashMap<>();
    private final List<Task> latestTasks = new ArrayList<>();
    private Project project;

    private FirestoreHelper firestoreHelper;
    private ListenerRegistration tasksListener, commentsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advisor_projects_view);

        project = (Project) getIntent().getSerializableExtra("project");
        firestoreHelper = new FirestoreHelper();

        initializeViews();
        setupRecyclerView();

        if (project != null) {
            tvProjectTitle.setText(project.getProjectName());
            loadMemberNames();
            listenForTasks();
            listenForComments();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tasksListener != null) tasksListener.remove();
        if (commentsListener != null) commentsListener.remove();
    }

    private void initializeViews() {
        tvProjectTitle = findViewById(R.id.tvProjectTitle);
        tvOverallProgress = findViewById(R.id.tvOverallProgress);
        tvTotalTasks = findViewById(R.id.tvTotalTasks);
        tvCompletedTasks = findViewById(R.id.tvCompletedTasks);
        tvTotalMembers = findViewById(R.id.tvTotalMembers);
        progressOverall = findViewById(R.id.progressOverall);
        rvMemberProgress = findViewById(R.id.rvMemberProgress);
        btnBack = findViewById(R.id.btnBack);
        etFeedback = findViewById(R.id.etFeedback);
        btnPostFeedback = findViewById(R.id.btnPostFeedback);
        commentsContainer = findViewById(R.id.commentsContainer);
        tvNoFeedback = findViewById(R.id.tvNoFeedback);

        btnBack.setOnClickListener(v -> onBackPressed());
        btnPostFeedback.setOnClickListener(v -> postFeedback());
    }

    private void setupRecyclerView() {
        memberProgressAdapter = new MemberProgressAdapter(memberProgressList);
        rvMemberProgress.setLayoutManager(new LinearLayoutManager(this));
        rvMemberProgress.setAdapter(memberProgressAdapter);
    }

    /** Students of the project: everyone in memberIds (the Project Manager is included). */
    private List<String> studentEmails() {
        List<String> emails = new ArrayList<>();
        if (project.getMemberIds() != null) {
            for (String e : project.getMemberIds()) {
                String n = DataContract.normalizeEmail(e);
                if (n != null && !emails.contains(n)) emails.add(n);
            }
        }
        return emails;
    }

    private void loadMemberNames() {
        for (String email : studentEmails()) {
            firestoreHelper.getUserProfileByEmail(email, new FirestoreHelper.UserCallback() {
                @Override
                public void onSuccess(User user) {
                    if (user.getName() != null && !user.getName().isEmpty()) {
                        nameByEmail.put(email, user.getName());
                        recalculate();
                    }
                }

                @Override
                public void onError(String error) {
                    // Keep showing the email
                }
            });
        }
    }

    /** Overall and per-member progress update live when a task status changes (FR-15). */
    private void listenForTasks() {
        tasksListener = firestoreHelper.getProjectTasks(project.getProjectId(),
                new FirestoreHelper.TaskCallback() {
                    @Override
                    public void onSuccess(List<Task> tasks) {
                        latestTasks.clear();
                        latestTasks.addAll(tasks);
                        recalculate();
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(AdvisorProjectViewActivity.this,
                                "Could not load tasks: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void recalculate() {
        List<String> students = studentEmails();

        int total = latestTasks.size();
        int done = 0;
        for (Task t : latestTasks) if (t.isCompleted()) done++;
        int overall = total > 0 ? (done * 100) / total : 0;

        tvTotalTasks.setText(String.valueOf(total));
        tvCompletedTasks.setText(String.valueOf(done));
        tvTotalMembers.setText(String.valueOf(students.size()));
        tvOverallProgress.setText(overall + "%");
        progressOverall.setProgress(overall);

        memberProgressList.clear();
        for (String email : students) {
            int assigned = 0, completed = 0;
            for (Task t : latestTasks) {
                if (email.equals(DataContract.normalizeEmail(t.getAssigneeId()))) {
                    assigned++;
                    if (t.isCompleted()) completed++;
                }
            }
            int pct = assigned > 0 ? (completed * 100) / assigned : 0;
            String status = assigned == 0 ? "On Track" : pct >= 80 ? "Ahead" : pct >= 50 ? "On Track" : "Behind";
            String name = nameByEmail.containsKey(email) ? nameByEmail.get(email) : email;
            memberProgressList.add(new MemberProgress(email, name, email, completed, assigned, status));
        }
        memberProgressAdapter.notifyDataSetChanged();
    }

    private void listenForComments() {
        commentsListener = firestoreHelper.listenComments(project.getProjectId(),
                new FirestoreHelper.CommentCallback() {
                    @Override
                    public void onSuccess(List<Comment> comments) {
                        tvNoFeedback.setVisibility(comments.isEmpty() ? View.VISIBLE : View.GONE);
                        CommentViews.render(AdvisorProjectViewActivity.this, commentsContainer, comments);
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(AdvisorProjectViewActivity.this,
                                "Could not load feedback: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** T8 Leave feedback: comment + activity log entry in one batch. */
    private void postFeedback() {
        String text = etFeedback.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            etFeedback.setError("Feedback is empty");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String name = user != null && user.getDisplayName() != null ? user.getDisplayName() : "Teacher";

        btnPostFeedback.setEnabled(false);
        firestoreHelper.postComment(project.getProjectId(), name, text, (success, message) -> {
            btnPostFeedback.setEnabled(true);
            if (success) {
                etFeedback.setText("");
                Toast.makeText(this, "Feedback posted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Not posted: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
