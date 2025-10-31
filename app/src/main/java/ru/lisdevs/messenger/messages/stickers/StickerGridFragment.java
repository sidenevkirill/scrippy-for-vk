package ru.lisdevs.messenger.messages.stickers;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Sticker;

import android.util.Log;
import android.widget.LinearLayout;

public class StickerGridFragment extends Fragment {
    private static final String TAG = "StickerGridFragment";
    private static final String ARG_STICKERS = "stickers";

    private List<Sticker> stickers;
    private StickersAdapter adapter;
    private RecyclerView recyclerView;
    private LinearLayout emptyState;

    public interface OnStickerClickListener {
        void onStickerClick(Sticker sticker);
    }

    public static StickerGridFragment newInstance(List<Sticker> stickers) {
        StickerGridFragment fragment = new StickerGridFragment();
        Bundle args = new Bundle();
        args.putParcelableArrayList(ARG_STICKERS, new ArrayList<>(stickers));
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            stickers = getArguments().getParcelableArrayList(ARG_STICKERS);
        }
        if (stickers == null) {
            stickers = new ArrayList<>();
        }
        Log.d(TAG, "Fragment created with " + stickers.size() + " stickers");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sticker_grid, container, false);

        initViews(view);
        setupRecyclerView();
        updateEmptyState();

        return view;
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.stickersRecyclerView);
        emptyState = view.findViewById(R.id.emptyState);
    }

    private void setupRecyclerView() {
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 4);
        recyclerView.setLayoutManager(layoutManager);

        // Добавьте проверку ресурсов
        if (getResources() != null) {
            int spacingInPixels = getResources().getDimensionPixelSize(R.dimen.sticker_spacing);
            recyclerView.addItemDecoration(new StickerGridItemDecoration(4, spacingInPixels, true));
        }

        adapter = new StickersAdapter(stickers, sticker -> {
            if (getActivity() instanceof OnStickerClickListener) {
                ((OnStickerClickListener) getActivity()).onStickerClick(sticker);
            }
        });

        // Принудительно установите размер
        adapter.setPreferredStickerSize(128);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);

        Log.d(TAG, "RecyclerView setup completed. Stickers count: " +
                (stickers != null ? stickers.size() : 0));
    }

    private void updateEmptyState() {
        if (stickers == null || stickers.isEmpty()) {
            if (recyclerView != null) {
                recyclerView.setVisibility(View.GONE);
            }
            if (emptyState != null) {
                emptyState.setVisibility(View.VISIBLE);
            }
            Log.d(TAG, "Empty state shown");
        } else {
            if (recyclerView != null) {
                recyclerView.setVisibility(View.VISIBLE);
            }
            if (emptyState != null) {
                emptyState.setVisibility(View.GONE);
            }
            Log.d(TAG, "Stickers shown: " + stickers.size());
        }
    }

    public void setStickers(List<Sticker> newStickers) {
        this.stickers = newStickers != null ? newStickers : new ArrayList<>();
        if (adapter != null) {
            adapter.setStickers(this.stickers);
            updateEmptyState();
        }
        Log.d(TAG, "Stickers updated: " + this.stickers.size() + " items");

        // Принудительное обновление UI
        if (recyclerView != null) {
            recyclerView.post(() -> {
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            });
        }
    }
}