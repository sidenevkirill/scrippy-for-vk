package ru.lisdevs.messenger.groups;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Group;

public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GroupViewHolder> {

    private List<Group> groups;
    private OnGroupClickListener listener;

    public interface OnGroupClickListener {
        void onGroupClick(Group group);
    }

    public void setOnGroupClickListener(OnGroupClickListener listener) {
        this.listener = listener;
    }

    public GroupAdapter(List<Group> groups) {
        this.groups = groups;
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view= LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_friend, parent,false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        final Group group=groups.get(position);
        holder.textView.setText(group.name); // отображаем название группы

        // Получаем первую букву названия группы
        if (group.name != null && !group.name.isEmpty()) {
            String initial = group.name.substring(0, 1).toUpperCase();
            holder.initialTextView.setText(initial);
        } else {
            holder.initialTextView.setText(""); // или дефолтное значение
        }

        // Устанавливаем обработчик клика
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onGroupClick(group);
            }
        });
    }

    public void updateData(List<Group> newGroups) {
        this.groups = new ArrayList<>(newGroups);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() { return groups.size(); }

    class GroupViewHolder extends RecyclerView.ViewHolder{
        TextView textView; // для названия группы
        TextView initialTextView; // для первой буквы

        public GroupViewHolder(@NonNull View itemView){
            super(itemView);
            textView= itemView.findViewById(R.id.user); // ваш существующий id для названия
            initialTextView= itemView.findViewById(R.id.image_name); // добавьте в layout и укажите правильный id
        }
    }
}