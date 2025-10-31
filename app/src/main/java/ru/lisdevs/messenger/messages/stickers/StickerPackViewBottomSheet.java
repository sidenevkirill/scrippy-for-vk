package ru.lisdevs.messenger.messages.stickers;


import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.model.Message;
import ru.lisdevs.messenger.model.Sticker;
import ru.lisdevs.messenger.model.StickerPack;
import ru.lisdevs.messenger.utils.GridSpacingItemDecoration;
import ru.lisdevs.messenger.utils.TokenManager;

public class StickerPackViewBottomSheet extends BottomSheetDialogFragment
        implements StickerPackViewAdapter.OnStickerClickListener {

    private static final String ARG_STICKER_PACK = "sticker_pack";

    private Toolbar toolbar;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyText;
    private StickerPackViewAdapter adapter;

    private StickerPack stickerPack;
    private List<Sticker> stickers = new ArrayList<>();
    private OkHttpClient httpClient = new OkHttpClient();

    private StickerShareListener stickerShareListener;

    public interface StickerShareListener {
        void onStickerSelected(Sticker sticker);
    }

    public static StickerPackViewBottomSheet newInstance(StickerPack stickerPack) {
        StickerPackViewBottomSheet fragment = new StickerPackViewBottomSheet();
        Bundle args = new Bundle();
        args.putParcelable(ARG_STICKER_PACK, stickerPack);
        fragment.setArguments(args);
        return fragment;
    }

    public void setStickerShareListener(StickerShareListener listener) {
        this.stickerShareListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            stickerPack = getArguments().getParcelable(ARG_STICKER_PACK);
        }

       // setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.AppBottomSheetDialogTheme);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_sticker_pack_view, container, false);

        initViews(view);
        setupToolbar();
        loadStickers();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Настраиваем поведение BottomSheet
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog != null) {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        }
    }

    private void initViews(View view) {
        toolbar = view.findViewById(R.id.toolbar);
        recyclerView = view.findViewById(R.id.recyclerViewStickers);
        progressBar = view.findViewById(R.id.progressBar);
        emptyText = view.findViewById(R.id.emptyText);

        // Настройка RecyclerView
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 4);
        recyclerView.setLayoutManager(layoutManager);

        // Добавляем отступы между элементами
        int spacing = getResources().getDimensionPixelSize(R.dimen.sticker_spacing);
        recyclerView.addItemDecoration(new GridSpacingItemDecoration(4, spacing, true));

        adapter = new StickerPackViewAdapter(stickers, this);
        recyclerView.setAdapter(adapter);
    }

    private void setupToolbar() {
        toolbar.setTitle(stickerPack != null ? stickerPack.getTitle() : "Стикерпак");

        // Показываем количество стикеров в подзаголовке
        TextView subtitle = toolbar.findViewById(R.id.toolbar_subtitle);
        if (subtitle != null && stickerPack != null) {
            int stickerCount = stickerPack.getStickers() != null ? stickerPack.getStickers().size() : 0;
            subtitle.setText(getString(R.string.tracks_count, stickerCount));
        }

        // Настройка кнопки "Назад"
        toolbar.setNavigationOnClickListener(v -> dismiss());

        // Меню для дополнительных действий
        toolbar.inflateMenu(R.menu.menu_sticker_pack);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_share) {
                shareStickerPack();
                return true;
            } else if (id == R.id.action_info) {
                showPackInfo();
                return true;
            }
            return false;
        });
    }

    private void loadStickers() {
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        // Загружаем стикеры в фоновом потоке
        new Thread(() -> {
            if (stickerPack != null && stickerPack.getStickers() != null) {
                stickers.clear();
                stickers.addAll(stickerPack.getStickers());
            }

            requireActivity().runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);

                if (stickers.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE);
                    emptyText.setText("В этом пакете нет стикеров");
                } else {
                    adapter.setStickers(stickers);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    @Override
    public void onStickerClick(Sticker sticker) {
        if (stickerShareListener != null) {
            stickerShareListener.onStickerSelected(sticker);
            dismiss();
        } else {
            // Если нет слушателя, показываем стандартный BottomSheet для отправки
            showStickerShareBottomSheet(sticker);
        }
    }

    private void showStickerShareBottomSheet(Sticker sticker) {
        StickerShareBottomSheet bottomSheet = StickerShareBottomSheet.newInstance(sticker);
        bottomSheet.setStickerShareListener((dialog, stickerToShare) -> {
            sendStickerAsImage(dialog, stickerToShare);
        });
        bottomSheet.show(getParentFragmentManager(), "sticker_share_bottom_sheet");
    }

    private void sendStickerAsImage(Message dialog, Sticker sticker) {
        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null) {
            Toast.makeText(requireContext(), "Ошибка авторизации", Toast.LENGTH_SHORT).show();
            return;
        }

        // Показываем прогресс
        ProgressDialog progressDialog = new ProgressDialog(requireContext());
        progressDialog.setMessage("Отправка стикера...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Загружаем изображение стикера и отправляем как фото
        loadAndSendStickerAsImage(sticker, dialog, progressDialog);
    }

    private void loadAndSendStickerAsImage(Sticker sticker, Message dialog, ProgressDialog progressDialog) {
        if (sticker == null || sticker.getImageUrl() == null) {
            progressDialog.dismiss();
            Toast.makeText(requireContext(), "Ошибка: неверный стикер", Toast.LENGTH_SHORT).show();
            return;
        }

        String imageUrl = sticker.getImageUrl();

        // Исправляем URL если нужно
        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            imageUrl = "https://" + imageUrl;
        }

        // Загружаем изображение стикера
        Request request = new Request.Builder()
                .url(imageUrl)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(requireContext(), "Ошибка загрузки стикера", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    // Получаем байты изображения
                    byte[] imageBytes = response.body().bytes();

                    // Отправляем как фото через VK API
                    uploadStickerAsPhoto(imageBytes, dialog, sticker, progressDialog);
                } else {
                    requireActivity().runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(requireContext(),
                                "Ошибка загрузки изображения: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void uploadStickerAsPhoto(byte[] imageBytes, Message dialog, Sticker sticker, ProgressDialog progressDialog) {
        String accessToken = TokenManager.getInstance(requireContext()).getToken();

        // Сначала получаем URL для загрузки
        String getUploadUrl = "https://api.vk.com/method/photos.getMessagesUploadServer" +
                "?access_token=" + accessToken +
                "&v=5.131";

        Request request = new Request.Builder()
                .url(getUploadUrl)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(requireContext(), "Ошибка получения сервера загрузки", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

                        if (json.has("response")) {
                            JSONObject uploadServer = json.getJSONObject("response");
                            String uploadUrl = uploadServer.getString("upload_url");

                            // Загружаем изображение на сервер
                            uploadImageToServer(imageBytes, uploadUrl, dialog, sticker, progressDialog);
                        } else {
                            throw new JSONException("No upload server in response");
                        }
                    } catch (JSONException e) {
                        requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(requireContext(), "Ошибка обработки ответа сервера", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    requireActivity().runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(requireContext(),
                                "Ошибка получения сервера загрузки: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void uploadImageToServer(byte[] imageBytes, String uploadUrl, Message dialog, Sticker sticker, ProgressDialog progressDialog) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("photo", "sticker.png",
                        RequestBody.create(imageBytes, MediaType.parse("image/png")))
                .build();

        Request request = new Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(requireContext(), "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        // Сохраняем фото в VK
                        saveMessagesPhoto(responseBody, dialog, sticker, progressDialog);
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(requireContext(), "Ошибка обработки ответа", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    requireActivity().runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(requireContext(),
                                "Ошибка загрузки изображения: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void saveMessagesPhoto(String uploadResponse, Message dialog, Sticker sticker, ProgressDialog progressDialog) {
        String accessToken = TokenManager.getInstance(requireContext()).getToken();

        try {
            JSONObject uploadJson = new JSONObject(uploadResponse);
            String server = uploadJson.getString("server");
            String photo = uploadJson.getString("photo");
            String hash = uploadJson.getString("hash");

            String saveUrl = "https://api.vk.com/method/photos.saveMessagesPhoto" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&server=" + server +
                    "&photo=" + URLEncoder.encode(photo, "UTF-8") +
                    "&hash=" + URLEncoder.encode(hash, "UTF-8");

            Request request = new Request.Builder()
                    .url(saveUrl)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(requireContext(), "Ошибка сохранения фото", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);

                            if (json.has("response")) {
                                JSONArray photos = json.getJSONArray("response");
                                JSONObject photo = photos.getJSONObject(0);

                                // Отправляем сообщение с фото
                                sendPhotoMessage(photo, dialog, sticker, progressDialog);
                            } else {
                                throw new JSONException("No response in save photo");
                            }
                        } catch (JSONException e) {
                            requireActivity().runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(requireContext(), "Ошибка сохранения фото", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(requireContext(),
                                    "Ошибка сохранения фото: " + response.code(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        } catch (Exception e) {
            requireActivity().runOnUiThread(() -> {
                progressDialog.dismiss();
                Toast.makeText(requireContext(), "Ошибка обработки фото", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void sendPhotoMessage(JSONObject photo, Message dialog, Sticker sticker, ProgressDialog progressDialog) {
        String accessToken = TokenManager.getInstance(requireContext()).getToken();

        try {
            int ownerId = photo.getInt("owner_id");
            int photoId = photo.getInt("id");
            String attachment = "photo" + ownerId + "_" + photoId;

            String url = "https://api.vk.com/method/messages.send" +
                    "?access_token=" + accessToken +
                    "&v=5.131" +
                    "&peer_id=" + dialog.getPeerId() +
                    "&attachment=" + URLEncoder.encode(attachment, "UTF-8") +
                    "&random_id=" + System.currentTimeMillis();

            Request request = new Request.Builder().url(url).build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(requireContext(), "Ошибка отправки стикера", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    requireActivity().runOnUiThread(() -> {
                        progressDialog.dismiss();

                        if (response.isSuccessful()) {
                            Toast.makeText(requireContext(),
                                    "Стикер отправлен в диалог с " + dialog.getSenderName(),
                                    Toast.LENGTH_SHORT).show();
                            dismiss(); // Закрываем BottomSheet после успешной отправки
                        } else {
                            Toast.makeText(requireContext(),
                                    "Ошибка отправки: " + response.code(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            requireActivity().runOnUiThread(() -> {
                progressDialog.dismiss();
                Toast.makeText(requireContext(), "Ошибка отправки фото", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void shareStickerPack() {
        if (stickerPack == null) return;

        String shareText = "Посмотрите на стикерпак: " + stickerPack.getTitle();
        if (stickerPack.getStickers() != null) {
            shareText += " (" + stickerPack.getStickers().size() + " стикеров)";
        }

        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareText);
        startActivity(android.content.Intent.createChooser(shareIntent, "Поделиться стикерпаком"));
    }

    private void showPackInfo() {
        if (stickerPack == null) return;

        int stickerCount = stickerPack.getStickers() != null ? stickerPack.getStickers().size() : 0;
        String info = "Название: " + stickerPack.getTitle() +
                "\nКоличество стикеров: " + stickerCount +
                "\nID пакета: " + stickerPack.getId();

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Информация о стикерпаке")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onStart() {
        super.onStart();

        // Настраиваем внешний вид BottomSheet
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog != null) {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackground(new ColorDrawable(Color.TRANSPARENT));
            }
        }
    }}