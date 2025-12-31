package ru.lisdevs.messenger.groups;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import ru.lisdevs.messenger.R;

public class GroupMenuBottomSheet extends BottomSheetDialogFragment {

    public interface GroupMenuListener {
        void onShareGroup();
        void onCopyLink();
        void onCopyId();
        void onGroupSettings();
    }

    private GroupMenuListener listener;
    private long groupId;
    private String groupName;

    public void setGroupMenuListener(GroupMenuListener listener) {
        this.listener = listener;
    }

    public void setGroupData(long groupId, String groupName) {
        this.groupId = groupId;
        this.groupName = groupName;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_group_menu, container, false);
        setupViews(view);
        return view;
    }

    private void setupViews(View view) {
        // Кнопка "Поделиться группой"
        TextView btnShareGroup = view.findViewById(R.id.btnShareGroup);
        if (btnShareGroup != null) {
            btnShareGroup.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onShareGroup();
                }
                dismiss();
            });
        }

        // Кнопка "Копировать ссылку"
        TextView btnCopyLink = view.findViewById(R.id.btnCopyLink);
        if (btnCopyLink != null) {
            btnCopyLink.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCopyLink();
                }
                dismiss();
            });
        }

        // Кнопка "Копировать ID"
        TextView btnCopyId = view.findViewById(R.id.btnCopyId);
        if (btnCopyId != null) {
            btnCopyId.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCopyId();
                }
                dismiss();
            });
        }

        // Кнопка "Настройки группы"
        TextView btnGroupSettings = view.findViewById(R.id.btnGroupSettings);
        if (btnGroupSettings != null) {
            btnGroupSettings.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onGroupSettings();
                }
                dismiss();
            });
        }

        // Кнопка "Закрыть"
        TextView btnClose = view.findViewById(R.id.btnClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        // Обновляем текст кнопок, если есть данные о группе
        if (groupId != 0) {
            TextView title = view.findViewById(R.id.textTitle);
            if (title != null && groupName != null) {
                title.setText(groupName);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog != null) {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        }
    }

    public static GroupMenuBottomSheet newInstance(long groupId, String groupName) {
        GroupMenuBottomSheet bottomSheet = new GroupMenuBottomSheet();
        bottomSheet.setGroupData(groupId, groupName);
        return bottomSheet;
    }
}