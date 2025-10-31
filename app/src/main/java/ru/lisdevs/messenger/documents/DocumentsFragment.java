package ru.lisdevs.messenger.documents;


import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.friends.PhotoViewerFragment;
import ru.lisdevs.messenger.model.Document;
import ru.lisdevs.messenger.utils.TokenManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import android.widget.ImageButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;


public class DocumentsFragment extends Fragment {

    private AppBarLayout appBarLayout;
    private CollapsingToolbarLayout collapsingToolbar;
    private MaterialToolbar toolbar;
    private RecyclerView recyclerViewDocuments;
    private SwipeRefreshLayout swipeRefreshLayout;
    private DocumentsAdapter documentsAdapter;
    private List<Document> documentsList;

    // Header views
    private ImageView cover;
    private ImageView userAvatar;
    private TextView userName;
    private TextView userStatus;
    private TextView documentsCount;

    // Action buttons
    private MaterialButton downloadButton;
    private MaterialButton shareButton;
    private MaterialButton sortButton;
    private MaterialButton menuButton;

    private FrameLayout progressBarContainer;
    private TextView emptyStateText;

    private TokenManager tokenManager;
    private RequestQueue requestQueue;

    // Для управления загрузками
    private DownloadManager downloadManager;
    private Map<Long, String> downloadIds;
    private BroadcastReceiver downloadReceiver;

    private static final int STORAGE_PERMISSION_CODE = 1;

    public DocumentsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_documents, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        toolbar = view.findViewById(R.id.toolbar);
        tokenManager = TokenManager.getInstance(requireContext());
        requestQueue = Volley.newRequestQueue(requireContext());
        downloadManager = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
        downloadIds = new HashMap<>();

        initViews(view);
        setupToolbar();
        setupRecyclerView();
        setupSwipeRefresh();
        loadUserInfo();
        loadDocuments();
        setupClickListeners();
        registerDownloadReceiver();
    }

    private void initViews(View view) {
        appBarLayout = view.findViewById(R.id.appBarLayout);
        collapsingToolbar = view.findViewById(R.id.collapsingToolbar);
        recyclerViewDocuments = view.findViewById(R.id.recyclerViewDocuments);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        progressBarContainer = view.findViewById(R.id.progressBarContainer);
        emptyStateText = view.findViewById(R.id.emptyStateText);

        // Header views
        cover = view.findViewById(R.id.cover);
        userAvatar = view.findViewById(R.id.user_avatars);
        userName = view.findViewById(R.id.user_name);
        userStatus = view.findViewById(R.id.user_status);
        documentsCount = view.findViewById(R.id.documents_count);

        // Action buttons
        downloadButton = view.findViewById(R.id.download_button);
        shareButton = view.findViewById(R.id.share_button);
        sortButton = view.findViewById(R.id.sort_button);
        menuButton = view.findViewById(R.id.menu_button);
    }

    private void setupToolbar() {
        toolbar.setTitle("Мои документы");
        if (!isAdded() || getActivity() == null) return;

        ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_create_playlist) {
                return true;
            }
            return false;
        });
    }

    private void handleBackPressed() {
        // Пытаемся вернуться в back stack
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            // Если back stack пуст, закрываем activity
            requireActivity().finish();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_documents, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_search) {
            showSearchDialog();
            return true;
        } else if (id == R.id.action_upload) {
            uploadDocument();
            return true;
        } else if (id == R.id.action_select_all) {
            selectAllDocuments();
            return true;
        } else if (id == R.id.action_refresh) {
            loadDocuments();
            return true;
        } else if (id == R.id.action_settings) {
            openSettings();
            return true;
        } else if (id == R.id.action_sort_name) {
            sortDocumentsByName();
            return true;
        } else if (id == R.id.action_sort_date) {
            sortDocumentsByDate();
            return true;
        } else if (id == R.id.action_sort_size) {
            sortDocumentsBySize();
            return true;
        } else if (id == R.id.action_sort_type) {
            sortDocumentsByType();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView() {
        documentsList = new ArrayList<>();
        documentsAdapter = new DocumentsAdapter(documentsList, new DocumentsAdapter.OnDocumentClickListener() {
            @Override
            public void onDocumentClick(Document document) {
                openDocument(document);
            }

            @Override
            public void onDocumentDownload(Document document) {
                downloadDocument(document);
            }

            @Override
            public void onDocumentShare(Document document) {
                shareDocument(document);
            }

            @Override
            public void onDocumentLongClick(Document document) {
                showDocumentOptions(document);
            }
        });

        // Добавляем обработчик кликов на изображения
        documentsAdapter.setImageClickListener(document -> {
            openImageDocument(document);
        });

        recyclerViewDocuments.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewDocuments.setAdapter(documentsAdapter);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadDocuments();
        });
    }

    private void setupClickListeners() {
        downloadButton.setOnClickListener(v -> {
            // Показываем меню по короткому нажатию
            showDownloadMenu(v);
        });

        shareButton.setOnClickListener(v -> {
            // Поделиться документами
            shareAllDocuments();
        });

        sortButton.setOnClickListener(v -> {
            // Диалог сортировки
            showSortDialog();
        });

        menuButton.setOnClickListener(v -> {
            // Дополнительные опции
            showMoreOptionsMenu();
        });
    }

    private void showDownloadMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchor);

        // Надуваем меню из ресурсов
        popupMenu.getMenuInflater().inflate(R.menu.menu_download_options, popupMenu.getMenu());

        // Обработчик выбора пункта меню
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();

            if (id == R.id.action_download_all) {
                downloadAllDocuments();
                return true;
            } else if (id == R.id.action_download_selected) {
                downloadSelectedDocuments();
                return true;
            } else if (id == R.id.action_download_settings) {
                showDownloadSettings();
                return true;
            } else if (id == R.id.action_download_folder) {
                openDownloadFolder();
                return true;
            }
            return false;
        });

        // Показываем меню
        popupMenu.show();
    }

    private void downloadSelectedDocuments() {
        // Проверяем, есть ли выбранные документы
        List<Document> selectedDocuments = getSelectedDocuments();
        if (selectedDocuments.isEmpty()) {
            Toast.makeText(requireContext(), "Нет выбранных документов", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!checkStoragePermission()) {
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Скачать выбранные")
                .setMessage("Вы действительно хотите скачать " + selectedDocuments.size() + " документов?")
                .setPositiveButton("Скачать", (dialog, which) -> {
                    int successCount = 0;
                    for (Document document : selectedDocuments) {
                        try {
                            downloadDocument(document);
                            successCount++;
                            // Небольшая задержка между запросами
                            new Handler().postDelayed(() -> {}, 100);
                        } catch (Exception e) {
                            Log.e("DocumentsFragment", "Failed to download: " + document.getTitle(), e);
                        }
                    }
                    Toast.makeText(requireContext(), "Запущена загрузка " + successCount + " документов", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private List<Document> getSelectedDocuments() {
        // Здесь должна быть логика получения выбранных документов
        // Пока возвращаем пустой список - нужно реализовать выбор в адаптере
        List<Document> selected = new ArrayList<>();
        for (Document doc : documentsList) {
            // if (doc.isSelected()) selected.add(doc);
        }
        return selected;
    }

    private void showDownloadSettings() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Настройки загрузки")
                .setMessage("Выберите настройки для загрузки документов")
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    Toast.makeText(requireContext(), "Настройки сохранены", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void openDownloadFolder() {
        try {
            Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Не удалось открыть папку загрузок", Toast.LENGTH_SHORT).show();
        }
    }

    private void openImageDocument(Document document) {
        if (document == null || document.getUrl() == null) {
            return;
        }

        // Создаем список URL для просмотрщика (в данном случае только одно изображение)
        ArrayList<String> imageUrls = new ArrayList<>();
        imageUrls.add(document.getUrl());

        // Открываем PhotoViewerFragment
        PhotoViewerFragment fragment = PhotoViewerFragment.newInstance(imageUrls, 0);

        getParentFragmentManager()
                .beginTransaction()

                .replace(R.id.container, fragment)
                .addToBackStack("photo_viewer")
                .commit();
    }


    // Метод для проверки, является ли документ изображением
    private boolean isImageDocument(Document document) {
        if (document.getExtension() == null) return false;

        String ext = document.getExtension().toLowerCase();
        return ext.equals("jpg") || ext.equals("jpeg") ||
                ext.equals("png") || ext.equals("gif") ||
                ext.equals("bmp") || ext.equals("webp") ||
                (document.getType() != null && document.getType().toLowerCase().contains("изображение"));
    }

    private void loadUserInfo() {
        // Загружаем информацию о текущем пользователе из TokenManager
        String fullName = tokenManager.getFullName();
        String photoUrl = tokenManager.getPhotoUrl();

        if (userName != null) {
            userName.setText(fullName != null ? fullName : "Мои документы");
        }

        // TODO: Загрузить аватар пользователя с помощью Glide/Picasso
        // if (userAvatar != null && photoUrl != null) {
        //     Glide.with(this).load(photoUrl).into(userAvatar);
        // }
    }

    private void loadDocuments() {
        String accessToken = tokenManager.getToken();
        String userId = tokenManager.getUserId();

        if (accessToken == null || userId == null) {
            Toast.makeText(requireContext(), "Ошибка авторизации", Toast.LENGTH_SHORT).show();
            showEmptyState(true);
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        showLoading(true);

        // VK API метод docs.get для получения документов текущего пользователя
        String url = "https://api.vk.com/method/docs.get?" +
                "access_token=" + accessToken +
                "&owner_id=" + userId +
                "&count=100" +
                "&v=5.199";

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    try {
                        parseDocumentsResponse(response);
                    } catch (JSONException e) {
                        handleError("Ошибка обработки данных");
                    }
                    swipeRefreshLayout.setRefreshing(false);
                    showLoading(false);
                },
                error -> {
                    handleError("Ошибка загрузки: " + error.getMessage());
                    swipeRefreshLayout.setRefreshing(false);
                    showLoading(false);
                }
        );

        requestQueue.add(request);
    }

    private void parseDocumentsResponse(JSONObject response) throws JSONException {
        if (response.has("error")) {
            JSONObject error = response.getJSONObject("error");
            String errorMsg = error.getString("error_msg");
            handleError("VK API: " + errorMsg);
            return;
        }

        JSONObject data = response.getJSONObject("response");
        JSONArray items = data.getJSONArray("items");

        documentsList.clear();

        for (int i = 0; i < items.length(); i++) {
            JSONObject doc = items.getJSONObject(i);
            Document document = parseDocument(doc);
            if (document != null) {
                documentsList.add(document);
            }
        }

        documentsAdapter.notifyDataSetChanged();
        updateHeaderInfo();

        if (documentsList.isEmpty()) {
            showEmptyState(true);
        } else {
            showEmptyState(false);
        }
    }

    private Document parseDocument(JSONObject doc) throws JSONException {
        try {
            String id = String.valueOf(doc.getInt("id"));
            String title = doc.getString("title");
            long size = doc.getLong("size");
            String ext = doc.getString("ext");
            String url = doc.getString("url");
            long date = doc.getLong("date");
            String type = String.valueOf(doc.getInt("type"));

            // Определяем тип документа
            String documentType = getDocumentType(Integer.parseInt(type), ext);
            String sizeFormatted = formatSize(size);

            return new Document(id, title, documentType, sizeFormatted, url, date, ext);

        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getDocumentType(int type, String ext) {
        switch (type) {
            case 1: return "Текстовый документ";
            case 2: return "Архив";
            case 3: return "GIF";
            case 4: return "Изображение";
            case 5: return "Аудио";
            case 6: return "Видео";
            case 7: return "Электронная книга";
            case 8: return "Неизвестно";
            default: return ext.toUpperCase() + " файл";
        }
    }

    private String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }

    private void updateHeaderInfo() {
        if (documentsCount != null) {
            String countText = documentsList.size() + " документов";
            documentsCount.setText(countText);
        }

        if (userStatus != null) {
            userStatus.setVisibility(View.VISIBLE);
        }
    }

    private void openDocument(Document document) {
        if (isImageDocument(document)) {
            // Для изображений открываем просмотрщик
            openImageDocument(document);
        } else {
            // Для остальных документов показываем диалог с опциями
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(document.getTitle())
                    .setMessage("Выберите действие с документом")
                    .setPositiveButton("Скачать", (dialog, which) -> {
                        downloadDocument(document);
                    })
                    .setNeutralButton("Открыть в браузере", (dialog, which) -> {
                        openDocumentInBrowser(document);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        }
    }

    private void openDocumentInBrowser(Document document) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(document.getUrl()));
            startActivity(browserIntent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Не удалось открыть документ", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadDocument(Document document) {
        if (!checkStoragePermission()) {
            return;
        }

        if (document == null || document.getUrl() == null) {
            Toast.makeText(requireContext(), "Ошибка: неверные данные документа", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Создаем запрос на скачивание
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(document.getUrl()));

            // Устанавливаем заголовок для VK API
            request.addRequestHeader("User-Agent", "VKAndroidApp/5.52 (Android 7.1.1; SDK 25; arm64-v8a; OnePlus ONEPLUS A5000; ru; official)");

            // Устанавливаем заголовок для отображения в уведомлениях
            request.setTitle(document.getTitle());
            request.setDescription("Скачивание документа из VK");

            // Устанавливаем папку назначения
            String fileName = document.getTitle();
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "VK_Documents/" + fileName);

            // Разрешаем скачивание по мобильной сети и Wi-Fi
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);

            // Разрешаем скачивание при роуминге
            request.setAllowedOverRoaming(true);

            // Показываем уведомление о загрузке
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            // Запускаем загрузку
            long downloadId = downloadManager.enqueue(request);
            downloadIds.put(downloadId, document.getTitle());

            // Показываем уведомление об успешном запуске загрузки
            Toast.makeText(requireContext(), "Загрузка начата: " + document.getTitle(), Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(requireContext(), "Ошибка при запуске загрузки", Toast.LENGTH_LONG).show();
            Log.e("DocumentsFragment", "Download error", e);
        }
    }

    private void downloadAllDocuments() {
        if (documentsList.isEmpty()) {
            Toast.makeText(requireContext(), "Нет документов для скачивания", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!checkStoragePermission()) {
            return;
        }

        // Показываем диалог подтверждения для массового скачивания
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Скачать все документы")
                .setMessage("Вы действительно хотите скачать все " + documentsList.size() + " документов?")
                .setPositiveButton("Скачать", (dialog, which) -> {
                    // Запускаем загрузку каждого документа
                    int successCount = 0;
                    for (Document document : documentsList) {
                        try {
                            downloadDocument(document);
                            successCount++;
                            // Небольшая задержка между запросами
                            new Handler().postDelayed(() -> {}, 100);
                        } catch (Exception e) {
                            Log.e("DocumentsFragment", "Failed to download: " + document.getTitle(), e);
                        }
                    }
                    Toast.makeText(requireContext(), "Запущена загрузка " + successCount + " документов", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerDownloadReceiver() {
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadIds.containsKey(id)) {
                    String fileName = downloadIds.get(id);

                    // Проверяем статус загрузки
                    if (downloadManager != null) {
                        DownloadManager.Query query = new DownloadManager.Query();
                        query.setFilterById(id);
                        Cursor cursor = downloadManager.query(query);

                        if (cursor != null && cursor.moveToFirst()) {
                            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                            if (statusIndex != -1) {
                                int status = cursor.getInt(statusIndex);

                                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                    Toast.makeText(requireContext(), "Загрузка завершена: " + fileName, Toast.LENGTH_SHORT).show();
                                } else if (status == DownloadManager.STATUS_FAILED) {
                                    int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                                    if (reasonIndex != -1) {
                                        int reason = cursor.getInt(reasonIndex);
                                        Toast.makeText(requireContext(), "Ошибка загрузки: " + getErrorReason(reason), Toast.LENGTH_LONG).show();
                                    }
                                }
                            }
                            cursor.close();
                        }
                    }
                    downloadIds.remove(id);
                }
            }
        };

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Для Android 13+ требуется явно указать флаг экспорта
                requireContext().registerReceiver(
                        downloadReceiver,
                        new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                        Context.RECEIVER_NOT_EXPORTED
                );
            } else {
                // Для версий ниже Android 13
                requireContext().registerReceiver(
                        downloadReceiver,
                        new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                );
            }
        } catch (Exception e) {
            Log.e("DocumentsFragment", "Error registering download receiver", e);
        }
    }

    private String getErrorReason(int reason) {
        switch (reason) {
            case DownloadManager.ERROR_CANNOT_RESUME:
                return "Невозможно возобновить загрузку";
            case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                return "Устройство хранения не найдено";
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                return "Файл уже существует";
            case DownloadManager.ERROR_FILE_ERROR:
                return "Ошибка файловой системы";
            case DownloadManager.ERROR_HTTP_DATA_ERROR:
                return "Ошибка HTTP данных";
            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                return "Недостаточно места на устройстве";
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                return "Слишком много перенаправлений";
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                return "Необработанный HTTP код";
            case DownloadManager.ERROR_UNKNOWN:
            default:
                return "Неизвестная ошибка";
        }
    }

    private void shareDocument(Document document) {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, document.getTitle());
            shareIntent.putExtra(Intent.EXTRA_TEXT, document.getTitle() + "\n\n" + document.getUrl());
            startActivity(Intent.createChooser(shareIntent, "Поделиться документом"));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Не удалось поделиться документом", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareAllDocuments() {
        if (documentsList.isEmpty()) {
            Toast.makeText(requireContext(), "Нет документов для отправки", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder shareText = new StringBuilder("Мои документы из VK:\n\n");
        for (Document doc : documentsList) {
            shareText.append("• ").append(doc.getTitle()).append(" (").append(doc.getSize()).append(")\n");
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Мои документы из VK");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        startActivity(Intent.createChooser(shareIntent, "Поделиться документами"));
    }

    private void showDocumentOptions(Document document) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(document.getTitle())
                .setItems(new String[]{"Скачать", "Поделиться", "Открыть в браузере", "Информация"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            downloadDocument(document);
                            break;
                        case 1:
                            shareDocument(document);
                            break;
                        case 2:
                            openDocumentInBrowser(document);
                            break;
                        case 3:
                            showDocumentInfo(document);
                            break;
                    }
                })
                .show();
    }

    private void showDocumentInfo(Document document) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Информация о документе")
                .setMessage(
                        "Название: " + document.getTitle() + "\n" +
                                "Тип: " + document.getType() + "\n" +
                                "Размер: " + document.getSize() + "\n" +
                                "Расширение: " + document.getExtension() + "\n" +
                                "Дата: " + new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date(document.getDate() * 1000))
                )
                .setPositiveButton("OK", null)
                .show();
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (requireContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Разрешение предоставлено", Toast.LENGTH_SHORT).show();
        } else if (requestCode == STORAGE_PERMISSION_CODE) {
            Toast.makeText(requireContext(), "Для скачивания файлов необходимо разрешение на запись", Toast.LENGTH_LONG).show();
        }
    }

    // Методы-заглушки для меню
    private void showSearchDialog() {
        Toast.makeText(requireContext(), "Поиск документов", Toast.LENGTH_SHORT).show();
    }

    private void uploadDocument() {
        Toast.makeText(requireContext(), "Загрузка документа", Toast.LENGTH_SHORT).show();
    }

    private void selectAllDocuments() {
        Toast.makeText(requireContext(), "Выбрать все документы", Toast.LENGTH_SHORT).show();
    }

    private void openSettings() {
        Toast.makeText(requireContext(), "Настройки", Toast.LENGTH_SHORT).show();
    }

    private void sortDocumentsByName() {
        Toast.makeText(requireContext(), "Сортировка по имени", Toast.LENGTH_SHORT).show();
    }

    private void sortDocumentsByDate() {
        Toast.makeText(requireContext(), "Сортировка по дате", Toast.LENGTH_SHORT).show();
    }

    private void sortDocumentsBySize() {
        Toast.makeText(requireContext(), "Сортировка по размеру", Toast.LENGTH_SHORT).show();
    }

    private void sortDocumentsByType() {
        Toast.makeText(requireContext(), "Сортировка по типу", Toast.LENGTH_SHORT).show();
    }

    private void showSortDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Сортировка документов")
                .setItems(new String[]{"По имени", "По дате", "По размеру", "По типу"}, (dialog, which) -> {
                    switch (which) {
                        case 0: sortDocumentsByName(); break;
                        case 1: sortDocumentsByDate(); break;
                        case 2: sortDocumentsBySize(); break;
                        case 3: sortDocumentsByType(); break;
                    }
                })
                .show();
    }

    private void showMoreOptionsMenu() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Дополнительные опции")
                .setItems(new String[]{"Очистить кэш", "Экспорт списка", "О приложении"}, (dialog, which) -> {
                    switch (which) {
                        case 0: clearCache(); break;
                        case 1: exportList(); break;
                        case 2: showAbout(); break;
                    }
                })
                .show();
    }

    private void clearCache() {
        Toast.makeText(requireContext(), "Очистка кэша", Toast.LENGTH_SHORT).show();
    }

    private void exportList() {
        Toast.makeText(requireContext(), "Экспорт списка", Toast.LENGTH_SHORT).show();
    }

    private void showAbout() {
        Toast.makeText(requireContext(), "О приложении", Toast.LENGTH_SHORT).show();
    }

    private void handleError(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        showEmptyState(true);
    }

    private void showLoading(boolean show) {
        if (progressBarContainer != null) {
            progressBarContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showEmptyState(boolean show) {
        if (emptyStateText != null) {
            emptyStateText.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                emptyStateText.setText("Документы не найдены");
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (appBarLayout != null) {
            //appBarLayout.removeOnOffsetChangedListener((AppBarLayout.OnOffsetChangedListener) appBarLayout);
        }
        if (requestQueue != null) {
            requestQueue.cancelAll(this);
        }

        // Отменяем регистрацию receiver
        if (downloadReceiver != null) {
            try {
                requireContext().unregisterReceiver(downloadReceiver);
            } catch (Exception e) {
                Log.e("DocumentsFragment", "Error unregistering receiver", e);
            }
        }

        // Очищаем список загрузок
        downloadIds.clear();
    }
}