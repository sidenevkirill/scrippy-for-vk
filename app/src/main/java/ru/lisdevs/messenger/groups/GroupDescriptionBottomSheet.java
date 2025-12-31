package ru.lisdevs.messenger.groups;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import ru.lisdevs.messenger.R;

public class GroupDescriptionBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_DESCRIPTION = "description";
    private static final String ARG_GROUP_NAME = "group_name";

    private String description;
    private String groupName;

    public static GroupDescriptionBottomSheet newInstance(String groupName, String description) {
        GroupDescriptionBottomSheet fragment = new GroupDescriptionBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_NAME, groupName);
        args.putString(ARG_DESCRIPTION, description);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            description = getArguments().getString(ARG_DESCRIPTION);
            groupName = getArguments().getString(ARG_GROUP_NAME);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_group_description, container, false);

        // Заголовок
        TextView titleTextView = view.findViewById(R.id.textViewTitle);
        if (groupName != null && !groupName.isEmpty()) {
            titleTextView.setText(groupName);
            titleTextView.setText("Описание");
        } else {
            titleTextView.setText("Описание группы");
        }

        // Описание
        TextView descriptionTextView = view.findViewById(R.id.textViewDescription);
        if (description != null && !description.isEmpty() && !description.equals("null")) {
            descriptionTextView.setText(description);
        } else {
            descriptionTextView.setText("Описание отсутствует");
        }

        // Кнопка закрытия
        MaterialButton closeButton = view.findViewById(R.id.btnClose);
        closeButton.setOnClickListener(v -> dismiss());

        return view;
    }
}