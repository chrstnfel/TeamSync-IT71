package com.example.studentprojecttracker.activities;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.studentprojecttracker.models.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.example.studentprojecttracker.R;
import com.example.studentprojecttracker.adapters.ProjectAdapter;
import com.example.studentprojecttracker.models.Project;
import com.example.studentprojecttracker.utils.DataContract;
import com.example.studentprojecttracker.utils.FirestoreHelper;
import com.example.studentprojecttracker.utils.SessionManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public class ProjectsDashboardActivity extends AppCompatActivity implements ProjectAdapter.OnProjectClickListener {

    private RecyclerView rvProjects;
    private FloatingActionButton fabAddProject;
    private MaterialButton btnProjects, btnReminders;
    private ImageView btnLogout, btnProfile;
    private ProjectAdapter projectAdapter;
    private List<Project> projectList;
    private SessionManager sessionManager;
    private FirestoreHelper firestoreHelper;
    private FirebaseAuth mAuth;
    private boolean isAdvisorMode;
    private ProgressDialog progressDialog;
    private ListenerRegistration projectsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_projects_dashboard);

        sessionManager = new SessionManager(this);
        firestoreHelper = new FirestoreHelper();
        mAuth = FirebaseAuth.getInstance();

        String userRole = sessionManager.getUserRole();
        isAdvisorMode = getString(R.string.role_advisor).equals(userRole);

        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        loadUserProfilePicture();
        loadProjectsFromFirestore();

        // Hide FAB for advisors
        if (isAdvisorMode) {
            fabAddProject.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload profile picture when returning to this activity
        loadUserProfilePicture();
    }

    private void initializeViews() {
        rvProjects = findViewById(R.id.rvProjects);
        fabAddProject = findViewById(R.id.fabAddProject);
        btnProjects = findViewById(R.id.btnProjects);
//        btnReminders = findViewById(R.id.btnReminders);
        btnLogout = findViewById(R.id.btnLogout);
        btnProfile = findViewById(R.id.btnProfile);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Loading...");
        progressDialog.setCancelable(false);

        // Profile button click
        btnProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
        });
    }

    private void loadUserProfilePicture() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && user.getPhotoUrl() != null) {
            Glide.with(this)
                    .load(user.getPhotoUrl())
                    .circleCrop()
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .into(btnProfile);
        } else {
            // Set default avatar if no photo
            btnProfile.setImageResource(R.drawable.ic_avatar_placeholder);
        }
    }

    private void setupRecyclerView() {
        projectList = new ArrayList<>();
        projectAdapter = new ProjectAdapter(projectList, this, isAdvisorMode);
        rvProjects.setLayoutManager(new LinearLayoutManager(this));
        rvProjects.setAdapter(projectAdapter);
    }

    private void loadProjectsFromFirestore() {
        progressDialog.show();
        String currentUserId = sessionManager.getUserEmail();

        // Remove old listener if exists
        if (projectsListener != null) {
            projectsListener.remove();
        }

        projectsListener = firestoreHelper.getUserProjects(currentUserId, isAdvisorMode,
                new FirestoreHelper.ProjectCallback() {
                    @Override
                    public void onSuccess(List<Project> projects) {
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        projectList.clear();
                        projectList.addAll(projects);
                        projectAdapter.notifyDataSetChanged();

                        // M9: remind students of their deadlines once per visit
                        if (!isAdvisorMode && !remindersChecked) {
                            remindersChecked = true;
                            checkDeadlineReminders(new ArrayList<>(projects));
                        }
                    }

                    @Override
                    public void onError(String error) {
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        Toast.makeText(ProjectsDashboardActivity.this,
                                "Error loading projects: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ---------- M9 Deadline Reminders (FR-13, T9) ----------
    private boolean remindersChecked = false;

    /**
     * Collects the user's unfinished tasks that are overdue or due within
     * DataContract.REMINDER_DAYS across their projects and shows them once.
     * Reminders are generated in the app from due dates (no email/SMS).
     */
    private void checkDeadlineReminders(List<Project> projects) {
        if (projects.isEmpty()) return;

        List<String> lines = new ArrayList<>();
        final int[] pending = {projects.size()};
        long now = System.currentTimeMillis();
        java.text.SimpleDateFormat fmt =
                new java.text.SimpleDateFormat(DataContract.DUE_DATE_PATTERN, java.util.Locale.US);

        for (Project project : projects) {
            firestoreHelper.getDueSoonTasks(project.getProjectId(), new FirestoreHelper.TaskCallback() {
                @Override
                public void onSuccess(List<com.example.studentprojecttracker.models.Task> tasks) {
                    for (com.example.studentprojecttracker.models.Task t : tasks) {
                        String when = t.getDueDate();
                        try {
                            java.util.Date due = fmt.parse(t.getDueDate());
                            // Overdue only after the whole due day has passed
                            if (due != null && due.getTime() + 24L * 60 * 60 * 1000 < now) {
                                when = t.getDueDate() + " (overdue)";
                            }
                        } catch (java.text.ParseException ignored) {
                        }
                        lines.add("• " + t.getTaskName() + " — " + project.getProjectName()
                                + "\n   Due " + when + " · " + t.getStatus());
                    }
                    finishOne();
                }

                @Override
                public void onError(String error) {
                    finishOne();
                }

                private void finishOne() {
                    pending[0]--;
                    if (pending[0] == 0 && !lines.isEmpty() && !isFinishing()) {
                        new AlertDialog.Builder(ProjectsDashboardActivity.this)
                                .setTitle("Deadline reminders")
                                .setMessage(android.text.TextUtils.join("\n\n", lines))
                                .setPositiveButton("OK", null)
                                .show();
                    }
                }
            });
        }
    }

    private void setupClickListeners() {
        fabAddProject.setOnClickListener(v -> showAddProjectDialog());

        btnLogout.setOnClickListener(v -> logout());

//        btnReminders.setOnClickListener(v -> {
//            Toast.makeText(this, R.string.reminders_coming_soon, Toast.LENGTH_SHORT).show();
//        });
    }

    private void showAddProjectDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_project, null);

        TextInputEditText etProjectName = dialogView.findViewById(R.id.etProjectName);
        TextInputEditText etCourseCode = dialogView.findViewById(R.id.etCourseCode);
        TextInputEditText etProjectDueDate = dialogView.findViewById(R.id.etProjectDueDate);

        // Date picker for due date
        etProjectDueDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            DatePickerDialog datePicker = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        String date = (month + 1) + "/" + dayOfMonth + "/" + year;
                        etProjectDueDate.setText(date);
                    },
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)
            );
            datePicker.show();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btnCancelProject).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.btnCreateProject).setOnClickListener(v -> {
            String name = etProjectName.getText().toString().trim();
            String code = etCourseCode.getText().toString().trim();
            String dueDate = etProjectDueDate.getText().toString().trim();

            if (name.isEmpty() || code.isEmpty() || dueDate.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            progressDialog.setMessage("Creating project...");
            progressDialog.show();

            String projectId = UUID.randomUUID().toString();
            String currentUserId = sessionManager.getUserEmail();

            Project newProject = new Project(projectId, name, code, dueDate, currentUserId);

            firestoreHelper.createProject(newProject, new FirestoreHelper.OnCompleteListener() {
                @Override
                public void onComplete(boolean success, String message) {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    if (success) {
                        Toast.makeText(ProjectsDashboardActivity.this,
                                "Project created successfully", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        Toast.makeText(ProjectsDashboardActivity.this,
                                "Error: " + message, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });

        dialog.show();
    }

    private void showEditProjectDialog(Project project) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_project, null);

        TextInputEditText etProjectName = dialogView.findViewById(R.id.etProjectName);
        TextInputEditText etCourseCode = dialogView.findViewById(R.id.etCourseCode);
        TextInputEditText etProjectDueDate = dialogView.findViewById(R.id.etProjectDueDate);
        MaterialButton btnCreate = dialogView.findViewById(R.id.btnCreateProject);

        etProjectName.setText(project.getProjectName());
        etCourseCode.setText(project.getCourseCode());
        etProjectDueDate.setText(project.getDueDate());
        btnCreate.setText("Update Project");

        etProjectDueDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            DatePickerDialog datePicker = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        String date = (month + 1) + "/" + dayOfMonth + "/" + year;
                        etProjectDueDate.setText(date);
                    },
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)
            );
            datePicker.show();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btnCancelProject).setOnClickListener(v -> dialog.dismiss());

        btnCreate.setOnClickListener(v -> {
            String name = etProjectName.getText().toString().trim();
            String code = etCourseCode.getText().toString().trim();
            String dueDate = etProjectDueDate.getText().toString().trim();

            if (name.isEmpty() || code.isEmpty() || dueDate.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            progressDialog.setMessage("Updating project...");
            progressDialog.show();

            project.setProjectName(name);
            project.setCourseCode(code);
            project.setDueDate(dueDate);

            firestoreHelper.updateProject(project, new FirestoreHelper.OnCompleteListener() {
                @Override
                public void onComplete(boolean success, String message) {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    if (success) {
                        Toast.makeText(ProjectsDashboardActivity.this,
                                "Project updated successfully", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        Toast.makeText(ProjectsDashboardActivity.this,
                                "Error: " + message, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });

        dialog.show();
    }

    @Override
    public void onProjectClick(Project project) {
        if (isAdvisorMode) {
            Intent intent = new Intent(this, AdvisorProjectViewActivity.class);
            intent.putExtra("project", project);
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("project", project);
            startActivity(intent);
        }
    }

    @Override
    public void onProjectMenuClick(Project project, View view) {
        PopupMenu popup = new PopupMenu(this, view);

        // Section 4.4.3: only the Project Manager sees edit, invite and delete.
        if (isAdvisorMode || !sessionManager.isProjectManager(project)) {
            popup.getMenu().add("View Details");
        } else {
            popup.getMenu().add("Edit Project");
            popup.getMenu().add("Add Member");
            popup.getMenu().add("Add Mentor");
            popup.getMenu().add("Delete Project");
        }

        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            switch (title) {
                case "Edit Project":
                    showEditProjectDialog(project);
                    break;
                case "Add Member":
                    showAddMemberDialog(project);
                    break;
                case "Add Mentor":
                    showAddMentorDialog(project);
                    break;
                case "Delete Project":
                    deleteProject(project);
                    break;
                case "View Details":
                    onProjectClick(project);
                    break;
            }
            return true;
        });

        popup.show();
    }

    private void showAddMemberDialog(Project project) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_member, null);

        TextInputEditText etMemberEmail = dialogView.findViewById(R.id.etMemberEmail);
        TextInputEditText etMemberName = dialogView.findViewById(R.id.etMemberName);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.btnAdd).setOnClickListener(v -> {
            String email = DataContract.normalizeEmail(etMemberEmail.getText().toString());
            String name = etMemberName.getText().toString().trim();

            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etMemberEmail.setError("Enter a valid email");
                etMemberEmail.requestFocus();
                return;
            }

            if (name.isEmpty()) {
                etMemberName.setError("Name is required");
                etMemberName.requestFocus();
                return;
            }

            // Check if member already exists
            if (project.getMemberIds().contains(email)) {
                Toast.makeText(this, email + " is already a member", Toast.LENGTH_SHORT).show();
                return;
            }

            progressDialog.setMessage("Adding member...");
            progressDialog.show();

            // Add member email to project
            List<String> memberIds = new ArrayList<>(project.getMemberIds());
            memberIds.add(email);
            project.setMemberIds(memberIds);

            Log.d("AddMember", "Adding member: " + email + " (" + name + ")");
            Log.d("AddMember", "Updated memberIds: " + memberIds.toString());

            // T3 Invite member: project update + activity log entry in one atomic batch
            firestoreHelper.updateProject(project, DataContract.ACTION_MEMBER_INVITED,
                    DataContract.TARGET_MEMBER, email, new FirestoreHelper.OnCompleteListener() {
                @Override
                public void onComplete(boolean success, String message) {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    if (success) {
                        Toast.makeText(ProjectsDashboardActivity.this,
                                name + " added to project", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        // The invitee's users/{uid} profile is created by their own
                        // app when they sign up or log in (Security Rules allow a
                        // user to write only their own profile).
                    } else {
                        // Nothing was saved: undo the local change
                        project.getMemberIds().remove(email);
                        Toast.makeText(ProjectsDashboardActivity.this,
                                "Error: " + message, Toast.LENGTH_SHORT).show();
                        Log.e("AddMember", "Error adding member: " + message);
                    }
                }
            });
        });

        dialog.show();
    }

    private void showAddMentorDialog(Project project) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_member, null);

        TextInputEditText etMemberEmail = dialogView.findViewById(R.id.etMemberEmail);
        TextInputEditText etMemberName = dialogView.findViewById(R.id.etMemberName);
        MaterialButton btnAdd = dialogView.findViewById(R.id.btnAdd);

        etMemberEmail.setHint("Mentor Email");
        etMemberName.setHint("Mentor Name");
        btnAdd.setText("Add Mentor");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        btnAdd.setOnClickListener(v -> {
            String email = DataContract.normalizeEmail(etMemberEmail.getText().toString());
            String name = etMemberName.getText().toString().trim();

            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etMemberEmail.setError("Enter a valid email");
                etMemberEmail.requestFocus();
                return;
            }

            if (name.isEmpty()) {
                etMemberName.setError("Name is required");
                etMemberName.requestFocus();
                return;
            }

            // Check if mentor already exists
            if (project.getMentorIds().contains(email)) {
                Toast.makeText(this, email + " is already a mentor", Toast.LENGTH_SHORT).show();
                return;
            }

            progressDialog.setMessage("Adding mentor...");
            progressDialog.show();

            // Add mentor email to project
            List<String> mentorIds = new ArrayList<>(project.getMentorIds());
            mentorIds.add(email);
            project.setMentorIds(mentorIds);

            Log.d("AddMentor", "Adding mentor: " + email + " (" + name + ")");

            // T3 Invite teacher: project update + activity log entry in one atomic batch
            firestoreHelper.updateProject(project, DataContract.ACTION_TEACHER_INVITED,
                    DataContract.TARGET_MEMBER, email, new FirestoreHelper.OnCompleteListener() {
                @Override
                public void onComplete(boolean success, String message) {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    if (success) {
                        Toast.makeText(ProjectsDashboardActivity.this,
                                name + " added as mentor", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        // Nothing was saved: undo the local change
                        project.getMentorIds().remove(email);
                        Toast.makeText(ProjectsDashboardActivity.this,
                                "Error: " + message, Toast.LENGTH_SHORT).show();
                        Log.e("AddMentor", "Error adding mentor: " + message);
                    }
                }
            });
        });

        dialog.show();
    }

    private void deleteProject(Project project) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Project")
                .setMessage("Are you sure you want to delete " + project.getProjectName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    progressDialog.setMessage("Deleting project...");
                    progressDialog.show();

                    firestoreHelper.deleteProject(project.getProjectId(),
                            new FirestoreHelper.OnCompleteListener() {
                                @Override
                                public void onComplete(boolean success, String message) {
                                    if (progressDialog.isShowing()) {
                                        progressDialog.dismiss();
                                    }
                                    if (success) {
                                        Toast.makeText(ProjectsDashboardActivity.this,
                                                "Project deleted", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(ProjectsDashboardActivity.this,
                                                "Error: " + message, Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        sessionManager.logout();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (projectsListener != null) {
            projectsListener.remove();
            projectsListener = null;
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}