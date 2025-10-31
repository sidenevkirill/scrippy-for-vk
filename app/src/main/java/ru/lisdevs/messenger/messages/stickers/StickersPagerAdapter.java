package ru.lisdevs.messenger.messages.stickers;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

import ru.lisdevs.messenger.model.Sticker;
import ru.lisdevs.messenger.model.StickerPack;

import android.util.Log;

public class StickersPagerAdapter extends FragmentStateAdapter {
    private static final String TAG = "StickersPagerAdapter";
    private List<StickerPack> stickerPacks;

    public StickersPagerAdapter(@NonNull FragmentActivity fragmentActivity,
                                List<StickerPack> stickerPacks) {
        super(fragmentActivity);
        this.stickerPacks = stickerPacks != null ? stickerPacks : new ArrayList<>();
        Log.d(TAG, "Pager adapter created with " + this.stickerPacks.size() + " packs");
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Log.d(TAG, "Creating fragment for position: " + position + ", total packs: " + stickerPacks.size());

        if (position == 0) {
            List<Sticker> allStickers = new ArrayList<>();
            for (StickerPack pack : stickerPacks) {
                if (pack != null && pack.getStickers() != null) {
                    allStickers.addAll(pack.getStickers());
                    Log.d(TAG, "Pack '" + pack.getTitle() + "' has " + pack.getStickers().size() + " stickers");
                }
            }
            Log.d(TAG, "All stickers tab: " + allStickers.size() + " stickers total");
            return StickerGridFragment.newInstance(allStickers);
        } else {
            int packIndex = position - 1;
            if (packIndex < stickerPacks.size() && stickerPacks.get(packIndex) != null) {
                StickerPack pack = stickerPacks.get(packIndex);
                List<Sticker> packStickers = pack.getStickers() != null ? pack.getStickers() : new ArrayList<>();
                Log.d(TAG, "Creating fragment for pack: " + pack.getTitle() + " with " + packStickers.size() + " stickers");

                // Проверьте первый стикер в пачке
                if (!packStickers.isEmpty()) {
                    Sticker firstSticker = packStickers.get(0);
                    Log.d(TAG, "First sticker in pack: " + firstSticker.getName() +
                            ", URL: " + firstSticker.getOptimalImageUrl(128));
                }

                return StickerGridFragment.newInstance(packStickers);
            } else {
                Log.e(TAG, "Invalid pack index: " + packIndex);
                return StickerGridFragment.newInstance(new ArrayList<>());
            }
        }
    }

    @Override
    public int getItemCount() {
        int count = stickerPacks.size() + 1;
        Log.d(TAG, "Item count: " + count);
        return count;
    }

    public void setStickerPacks(List<StickerPack> stickerPacks) {
        this.stickerPacks = stickerPacks != null ? stickerPacks : new ArrayList<>();
        Log.d(TAG, "Sticker packs updated: " + this.stickerPacks.size() + " packs");
        notifyDataSetChanged();

        // Логируем детальную информацию о каждом пакете
        for (int i = 0; i < this.stickerPacks.size(); i++) {
            StickerPack pack = this.stickerPacks.get(i);
            if (pack != null) {
                Log.d(TAG, "Pack " + i + ": " + pack.getTitle() +
                        ", stickers: " + (pack.getStickers() != null ? pack.getStickers().size() : 0));
            }
        }
    }
}