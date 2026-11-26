package com.example.studentprojecttracker.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.studentprojecttracker.R;
import com.example.studentprojecttracker.models.Comment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Draws teacher feedback (M7) as simple cards; used by the teacher view and the Team tab. */
public final class CommentViews {

    private CommentViews() {}

    public static void render(Context context, LinearLayout container, List<Comment> comments) {
        container.removeAllViews();
        SimpleDateFormat fmt = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
        int pad = dp(context, 12);

        for (Comment c : comments) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(pad, pad, pad, pad);
            card.setBackgroundResource(R.drawable.rounded_background);
            card.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.card_background_task));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(context, 8);
            card.setLayoutParams(lp);

            TextView header = new TextView(context);
            String name = c.getTeacherName() != null ? c.getTeacherName() : c.getTeacherId();
            String when = c.getCreatedAt() > 0 ? " · " + fmt.format(new Date(c.getCreatedAt())) : "";
            header.setText(name + when);
            header.setTypeface(Typeface.DEFAULT_BOLD);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            header.setTextColor(ContextCompat.getColor(context, R.color.text_primary));

            TextView body = new TextView(context);
            body.setText(c.getText());
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            body.setPadding(0, dp(context, 4), 0, 0);

            card.addView(header);
            card.addView(body);
            container.addView(card);
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
