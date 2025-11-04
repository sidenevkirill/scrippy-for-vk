package ru.lisdevs.messenger.settings;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebStorage;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.VKAuthActivity;
import ru.lisdevs.messenger.service.AlwaysOnlineService;
import ru.lisdevs.messenger.utils.PinManager;
import ru.lisdevs.messenger.utils.TokenManager;
import com.squareup.picasso.Picasso;


public class SettingsFragment extends Fragment {

    private static final String PREF_NAME = "VK_PREFS";
    private static final String PREF_DARK_THEME = "dark_theme_enabled";
    private static final String PREF_NOTIFICATIONS_ENABLED = "notifications_enabled";
    private static final String PREF_CHAT_BACKGROUND = "chat_background";
    private static final String PREF_MARK_AS_READ = "mark_as_read_enabled";
    private static final String PREF_ALWAYS_ONLINE = "always_online_enabled";
    private static final String PREF_SOUND_ENABLED = "sound_enabled";
    private static final String PREF_VIBRATION_ENABLED = "vibration_enabled";
    private static final String PREF_SEND_STICKERS_AS_STICKERS = "send_stickers_as_stickers";

    private Toolbar toolbar;
    private SwitchCompat themeSwitch;
    private SwitchCompat notificationsSwitch;
    private SwitchCompat markAsReadSwitch;
    private SwitchCompat alwaysOnlineSwitch;
    private SwitchCompat soundSwitch;
    private SwitchCompat vibrationSwitch;
    private SwitchCompat stickersAsStickersSwitch;
    private LinearLayout changeBackgroundButton;
    private LinearLayout pinSettingsButton;
    private SwitchCompat pinSwitch;
    private SwitchCompat fingerprintSwitch;

    // Элементы профиля
    private TextView userNameTextView;
    private ImageView userAvatarImageView;
    private String userId;
    private String userFirstName = "";
    private String userLastName = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        setupToolbar();

        // Инициализация элементов профиля
        initProfileViews(view);

        // Настройка переключателей
        setupThemeSwitch(view);
        setupNotificationsSwitch(view);
        setupMarkAsReadSwitch(view);
        setupAlwaysOnlineSwitch(view);
        setupStickersAsStickersSwitch(view);
        setupBackgroundButton(view);
        setupPinSettings(view);

        // Кнопка выхода
        LinearLayout logoutButton = view.findViewById(R.id.btn_logout);
        logoutButton.setOnClickListener(v -> {
            logout(requireContext());
            navigateToLogin();
        });

        // Кнопка эквалайзера
        LinearLayout equalizerButton = view.findViewById(R.id.btn_equalizer);
        if (equalizerButton != null) {
            equalizerButton.setOnClickListener(v -> openSystemEqualizer());
        }

        // Загружаем данные пользователя
        loadUserProfile();

        return view;
    }

    @SuppressLint("WrongViewCast")
    private void initProfileViews(View view) {
        userNameTextView = view.findViewById(R.id.user_name);
        userAvatarImageView = view.findViewById(R.id.avatars);

        // Получаем userId из TokenManager
        userId = TokenManager.getInstance(requireContext()).getUserId();

        // Устанавливаем стандартные значения
        if (userId != null) {
            userNameTextView.setText("Загрузка...");
        } else {
            userNameTextView.setText("Гость");
        }
    }

    private void setupToolbar() {
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);

            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
                activity.getSupportActionBar().setTitle("Настройки");
            }

            toolbar.setNavigationOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            });
        }
    }

    private void setupThemeSwitch(View view) {
        themeSwitch = view.findViewById(R.id.theme_switch);

        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(PREF_DARK_THEME, false);

        themeSwitch.setChecked(isDarkTheme);

        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveThemePreference(isChecked);
            applyTheme(isChecked);
        });
    }

    private void setupNotificationsSwitch(View view) {
        notificationsSwitch = view.findViewById(R.id.notifications_switch);

        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean notificationsEnabled = prefs.getBoolean(PREF_NOTIFICATIONS_ENABLED, true);

        notificationsSwitch.setChecked(notificationsEnabled);

        notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveNotificationsPreference(isChecked);
            applyNotificationsSetting(isChecked);
        });
    }

    private void setupMarkAsReadSwitch(View view) {
        markAsReadSwitch = view.findViewById(R.id.mark_as_read_switch);

        if (markAsReadSwitch == null) {
            Log.e("SettingsFragment", "markAsReadSwitch not found in layout");
            return;
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean markAsReadEnabled = prefs.getBoolean(PREF_MARK_AS_READ, true);

        markAsReadSwitch.setChecked(markAsReadEnabled);

        markAsReadSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveMarkAsReadPreference(isChecked);
            applyMarkAsReadSetting(isChecked);
            broadcastMarkAsReadState(isChecked);
        });
    }

    private void setupAlwaysOnlineSwitch(View view) {
        alwaysOnlineSwitch = view.findViewById(R.id.always_online_switch);

        if (alwaysOnlineSwitch == null) {
            Log.e("SettingsFragment", "alwaysOnlineSwitch not found in layout");
            return;
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean alwaysOnlineEnabled = prefs.getBoolean(PREF_ALWAYS_ONLINE, false);

        alwaysOnlineSwitch.setChecked(alwaysOnlineEnabled);

        alwaysOnlineSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveAlwaysOnlinePreference(isChecked);
            applyAlwaysOnlineSetting(isChecked);
            broadcastAlwaysOnlineState(isChecked);
        });
    }

    private void setupStickersAsStickersSwitch(View view) {
        stickersAsStickersSwitch = view.findViewById(R.id.stickers_as_stickers_switch);

        if (stickersAsStickersSwitch == null) {
            Log.e("SettingsFragment", "stickersAsStickersSwitch not found in layout");
            return;
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // ИЗМЕНЕНИЕ: по умолчанию false (выключено)
        boolean stickersAsStickersEnabled = prefs.getBoolean(PREF_SEND_STICKERS_AS_STICKERS, false);

        stickersAsStickersSwitch.setChecked(stickersAsStickersEnabled);

        stickersAsStickersSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveStickersAsStickersPreference(isChecked);
            applyStickersAsStickersSetting(isChecked);
            broadcastStickersAsStickersState(isChecked);
        });
    }

    private void setupBackgroundButton(View view) {
        changeBackgroundButton = view.findViewById(R.id.btn_change_background);

        if (changeBackgroundButton != null) {
            changeBackgroundButton.setOnClickListener(v -> showBackgroundSelectionDialog());
            updateBackgroundButtonPreview();
        }
    }

    private void setupPinSettings(View view) {
        pinSettingsButton = view.findViewById(R.id.btn_pin_settings);
        pinSwitch = view.findViewById(R.id.pin_switch);
        fingerprintSwitch = view.findViewById(R.id.fingerprint_switch);

        boolean pinEnabled = PinManager.isPinEnabled(requireContext());
        pinSwitch.setChecked(pinEnabled);

        pinSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                showPinSetupDialog();
            } else {
                showPinDisableDialog();
            }
        });

        boolean fingerprintEnabled = PinManager.isFingerprintEnabled(requireContext());
        fingerprintSwitch.setChecked(fingerprintEnabled);
        fingerprintSwitch.setEnabled(pinEnabled);

        fingerprintSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PinManager.setFingerprintEnabled(requireContext(), isChecked);
            Toast.makeText(requireContext(),
                    isChecked ? "Отпечаток пальца включен" : "Отпечаток пальца отключен",
                    Toast.LENGTH_SHORT).show();
        });

        if (pinSettingsButton != null) {
            pinSettingsButton.setOnClickListener(v -> showPinManagementDialog());
        }
    }

    // MARK AS READ METHODS
    private void saveMarkAsReadPreference(boolean enabled) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_MARK_AS_READ, enabled).apply();
        Log.d("SettingsFragment", "Mark as read preference saved: " + enabled);
    }

    private void applyMarkAsReadSetting(boolean enabled) {
        if (enabled) {
            Toast.makeText(requireContext(), "Автоматическая отметка о прочтении включена", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "Автоматическая отметка о прочтении отключена", Toast.LENGTH_SHORT).show();
        }
    }

    private void broadcastMarkAsReadState(boolean enabled) {
        Intent intent = new Intent("MARK_AS_READ_STATE_CHANGED");
        intent.putExtra("mark_as_read_enabled", enabled);
        requireContext().sendBroadcast(intent);
        Log.d("SettingsFragment", "Broadcast sent: mark_as_read = " + enabled);
    }

    // ALWAYS ONLINE METHODS
    private void saveAlwaysOnlinePreference(boolean enabled) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_ALWAYS_ONLINE, enabled).apply();
        Log.d("SettingsFragment", "Always online preference saved: " + enabled);
    }

    private void applyAlwaysOnlineSetting(boolean enabled) {
        if (enabled) {
            startAlwaysOnlineService();
            Toast.makeText(requireContext(), "Режим 'Постоянно в сети' включен", Toast.LENGTH_SHORT).show();
        } else {
            stopAlwaysOnlineService();
            Toast.makeText(requireContext(), "Режим 'Постоянно в сети' отключен", Toast.LENGTH_SHORT).show();
        }
    }

    private void broadcastAlwaysOnlineState(boolean enabled) {
        Intent intent = new Intent("ALWAYS_ONLINE_STATE_CHANGED");
        intent.putExtra("always_online_enabled", enabled);
        requireContext().sendBroadcast(intent);
        Log.d("SettingsFragment", "Always online broadcast sent: " + enabled);
    }

    private void startAlwaysOnlineService() {
        try {
            Intent serviceIntent = new Intent(requireContext(), AlwaysOnlineService.class);
            requireContext().startService(serviceIntent);
            scheduleOnlinePings();
        } catch (Exception e) {
            Log.e("SettingsFragment", "Error starting always online service: " + e.getMessage());
        }
    }

    private void stopAlwaysOnlineService() {
        try {
            Intent serviceIntent = new Intent(requireContext(), AlwaysOnlineService.class);
            requireContext().stopService(serviceIntent);
            cancelOnlinePings();
        } catch (Exception e) {
            Log.e("SettingsFragment", "Error stopping always online service: " + e.getMessage());
        }
    }

    private void scheduleOnlinePings() {
        try {
            // Раскомментируйте если используете WorkManager
            /*
            PeriodicWorkRequest onlineWorkRequest =
                    new PeriodicWorkRequest.Builder(OnlineWorker.class, 15, TimeUnit.MINUTES)
                            .setConstraints(new Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build())
                            .build();

            WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                    "online_ping_work",
                    ExistingPeriodicWorkPolicy.KEEP,
                    onlineWorkRequest
            );
            */
        } catch (Exception e) {
            Log.e("SettingsFragment", "Error scheduling online pings: " + e.getMessage());
        }
    }

    private void cancelOnlinePings() {
        try {
            // Раскомментируйте если используете WorkManager
            // WorkManager.getInstance(requireContext()).cancelUniqueWork("online_ping_work");
        } catch (Exception e) {
            Log.e("SettingsFragment", "Error canceling online pings: " + e.getMessage());
        }
    }

    // STICKERS AS STICKERS METHODS
    private void saveStickersAsStickersPreference(boolean enabled) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_SEND_STICKERS_AS_STICKERS, enabled).apply();
        Log.d("SettingsFragment", "Send stickers as stickers preference saved: " + enabled);
    }

    private void applyStickersAsStickersSetting(boolean enabled) {
        if (enabled) {
            Toast.makeText(requireContext(), "Стикеры отправляются как стикеры", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "Стикеры отправляются как изображения", Toast.LENGTH_SHORT).show();
        }
    }

    private void broadcastStickersAsStickersState(boolean enabled) {
        Intent intent = new Intent("SETTINGS_CHANGED");
        intent.putExtra("send_stickers_as_stickers", enabled);
        requireContext().sendBroadcast(intent);
        Log.d("SettingsFragment", "Stickers as stickers broadcast sent: " + enabled);
    }

    // SOUND AND VIBRATION METHODS
    private void saveSoundPreference(boolean enabled) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_SOUND_ENABLED, enabled).apply();
    }

    private void applySoundSetting(boolean enabled) {
        Toast.makeText(requireContext(),
                enabled ? "Звук уведомлений включен" : "Звук уведомлений отключен",
                Toast.LENGTH_SHORT).show();
        broadcastSoundState(enabled);
    }

    private void saveVibrationPreference(boolean enabled) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_VIBRATION_ENABLED, enabled).apply();
    }

    private void applyVibrationSetting(boolean enabled) {
        Toast.makeText(requireContext(),
                enabled ? "Вибрация включена" : "Вибрация отключена",
                Toast.LENGTH_SHORT).show();
        broadcastVibrationState(enabled);
    }

    private void broadcastSoundState(boolean enabled) {
        Intent intent = new Intent("SOUND_STATE_CHANGED");
        intent.putExtra("sound_enabled", enabled);
        requireContext().sendBroadcast(intent);
    }

    private void broadcastVibrationState(boolean enabled) {
        Intent intent = new Intent("VIBRATION_STATE_CHANGED");
        intent.putExtra("vibration_enabled", enabled);
        requireContext().sendBroadcast(intent);
    }

    // PIN MANAGEMENT METHODS
    private void showPinSetupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Установка PIN-кода");

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_setup_pin, null);
        builder.setView(dialogView);

        EditText pinField1 = dialogView.findViewById(R.id.pin_1);
        EditText pinField2 = dialogView.findViewById(R.id.pin_2);
        EditText pinField3 = dialogView.findViewById(R.id.pin_3);
        EditText pinField4 = dialogView.findViewById(R.id.pin_4);

        EditText[] pinFields = {pinField1, pinField2, pinField3, pinField4};
        setupPinFieldsNavigation(pinFields);

        builder.setPositiveButton("Установить", (dialog, which) -> {
            String pin = getPinFromFields(pinFields);
            if (pin.length() == 4) {
                PinManager.setPin(requireContext(), pin);
                PinManager.setPinEnabled(requireContext(), true);
                if (fingerprintSwitch != null) {
                    fingerprintSwitch.setEnabled(true);
                }
                Toast.makeText(requireContext(), "PIN-код установлен", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Введите 4-значный PIN-код", Toast.LENGTH_SHORT).show();
                if (pinSwitch != null) {
                    pinSwitch.setChecked(false);
                }
            }
        });

        builder.setNegativeButton("Отмена", (dialog, which) -> {
            if (pinSwitch != null) {
                pinSwitch.setChecked(false);
            }
        });

        builder.show();
    }

    private void showPinDisableDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Отключение PIN-кода")
                .setMessage("Вы уверены, что хотите отключить PIN-защиту?")
                .setPositiveButton("Отключить", (dialog, which) -> {
                    PinManager.setPinEnabled(requireContext(), false);
                    PinManager.resetAuthentication(requireContext());
                    if (fingerprintSwitch != null) {
                        fingerprintSwitch.setEnabled(false);
                        fingerprintSwitch.setChecked(false);
                    }
                    Toast.makeText(requireContext(), "PIN-код отключен", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", (dialog, which) -> {
                    if (pinSwitch != null) {
                        pinSwitch.setChecked(true);
                    }
                })
                .show();
    }

    private void showPinManagementDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Управление PIN-кодом")
                .setItems(new String[]{"Изменить PIN-код", "Отключить PIN-код"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showChangePinDialog();
                            break;
                        case 1:
                            showPinDisableDialog();
                            break;
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showChangePinDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Изменение PIN-кода");

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_pin, null);
        builder.setView(dialogView);

        EditText[] currentPinFields = {
                dialogView.findViewById(R.id.current_pin_1),
                dialogView.findViewById(R.id.current_pin_2),
                dialogView.findViewById(R.id.current_pin_3),
                dialogView.findViewById(R.id.current_pin_4)
        };

        EditText[] newPinFields = {
                dialogView.findViewById(R.id.new_pin_1),
                dialogView.findViewById(R.id.new_pin_2),
                dialogView.findViewById(R.id.new_pin_3),
                dialogView.findViewById(R.id.new_pin_4)
        };

        EditText[] confirmPinFields = {
                dialogView.findViewById(R.id.confirm_pin_1),
                dialogView.findViewById(R.id.confirm_pin_2),
                dialogView.findViewById(R.id.confirm_pin_3),
                dialogView.findViewById(R.id.confirm_pin_4)
        };

        TextView errorMessage = dialogView.findViewById(R.id.error_message);

        setupPinFieldsNavigation(currentPinFields);
        setupPinFieldsNavigation(newPinFields);
        setupPinFieldsNavigation(confirmPinFields);

        builder.setPositiveButton("Изменить", (dialog, which) -> {
            String currentPin = getPinFromFields(currentPinFields);
            String newPin = getPinFromFields(newPinFields);
            String confirmPin = getPinFromFields(confirmPinFields);

            if (currentPin.length() != 4 || newPin.length() != 4 || confirmPin.length() != 4) {
                showErrorMessage(errorMessage, "Все поля должны быть заполнены");
                return;
            }

            if (!PinManager.verifyPin(requireContext(), currentPin)) {
                showErrorMessage(errorMessage, "Неверный текущий PIN-код");
                return;
            }

            if (!newPin.equals(confirmPin)) {
                showErrorMessage(errorMessage, "Новый PIN-код и подтверждение не совпадают");
                return;
            }

            if (newPin.equals(currentPin)) {
                showErrorMessage(errorMessage, "Новый PIN-код не должен совпадать с текущим");
                return;
            }

            PinManager.setPin(requireContext(), newPin);
            Toast.makeText(requireContext(), "PIN-код успешно изменен", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void setupPinFieldsNavigation(EditText[] pinFields) {
        for (int i = 0; i < pinFields.length; i++) {
            final int currentIndex = i;
            final int nextIndex = i + 1;
            final int prevIndex = i - 1;

            pinFields[i].addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1 && nextIndex < pinFields.length) {
                        pinFields[nextIndex].requestFocus();
                    }
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });

            pinFields[i].setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (pinFields[currentIndex].getText().length() == 0 && prevIndex >= 0) {
                        pinFields[prevIndex].requestFocus();
                        pinFields[prevIndex].setText("");
                    }
                }
                return false;
            });
        }
    }

    private String getPinFromFields(EditText[] pinFields) {
        StringBuilder pin = new StringBuilder();
        for (EditText field : pinFields) {
            pin.append(field.getText().toString());
        }
        return pin.toString();
    }

    private void showErrorMessage(TextView errorView, String message) {
        if (errorView != null) {
            errorView.setText(message);
            errorView.setVisibility(View.VISIBLE);
        }
    }

    // BACKGROUND METHODS
    private void showBackgroundSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Выбор фона чата");

        String[] backgrounds = {
                "Стандартный (по умолчанию)",
                "Градиент синий",
                "Градиент фиолетовый",
                "Градиент зеленый",
                "Градиент оранжевый",
                "Абстрактный узор",
                "Ночной город",
                "Природа",
                "Темная тема",
                "Светлая тема"
        };

        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int currentBackground = prefs.getInt(PREF_CHAT_BACKGROUND, 0);

        builder.setSingleChoiceItems(backgrounds, currentBackground, (dialog, which) -> {
            saveBackgroundPreference(which);
            updateBackgroundButtonPreview();
            showBackgroundPreview(which);
            dialog.dismiss();
        });

        builder.setPositiveButton("Предпросмотр", (dialog, which) -> {
            showFullScreenBackgroundPreview();
        });

        builder.setNegativeButton("Отмена", null);
        builder.setNeutralButton("Сбросить", (dialog, which) -> {
            saveBackgroundPreference(0);
            updateBackgroundButtonPreview();
            Toast.makeText(requireContext(), "Фон сброшен к стандартному", Toast.LENGTH_SHORT).show();
        });

        builder.show();
    }

    private void showBackgroundPreview(int backgroundId) {
        AlertDialog.Builder previewBuilder = new AlertDialog.Builder(requireContext());
        previewBuilder.setTitle("Предпросмотр фона");

        View previewView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_background_preview, null);
        ImageView previewImage = previewView.findViewById(R.id.preview_image);
        TextView previewText = previewView.findViewById(R.id.preview_text);

        setPreviewBackground(previewImage, backgroundId);

        String[] backgroundNames = {
                "Стандартный (по умолчанию)",
                "Градиент синий",
                "Градиент фиолетовый",
                "Градиент зеленый",
                "Градиент оранжевый",
                "Абстрактный узор",
                "Ночной город",
                "Природа",
                "Темная тема",
                "Светлая тема"
        };

        previewText.setText(backgroundNames[backgroundId]);
        previewBuilder.setView(previewView);
        previewBuilder.setPositiveButton("Применить", (dialog, which) -> {
            saveBackgroundPreference(backgroundId);
            updateBackgroundButtonPreview();
            Toast.makeText(requireContext(), "Фон применен", Toast.LENGTH_SHORT).show();
            broadcastBackgroundChanged(backgroundId);
        });

        previewBuilder.setNegativeButton("Назад", (dialog, which) -> {
            showBackgroundSelectionDialog();
        });

        previewBuilder.show();
    }

    private void showFullScreenBackgroundPreview() {
        Dialog fullScreenDialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        fullScreenDialog.setContentView(R.layout.dialog_fullscreen_background_preview);

        ImageView fullPreview = fullScreenDialog.findViewById(R.id.full_preview_image);
        TextView fullPreviewText = fullScreenDialog.findViewById(R.id.full_preview_text);
        Button btnApply = fullScreenDialog.findViewById(R.id.btn_apply);
        Button btnBack = fullScreenDialog.findViewById(R.id.btn_back);

        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int currentBackground = prefs.getInt(PREF_CHAT_BACKGROUND, 0);

        setPreviewBackground(fullPreview, currentBackground);

        String[] backgroundNames = {
                "Стандартный (по умолчанию)",
                "Градиент синий",
                "Градиент фиолетовый",
                "Градиент зеленый",
                "Градиент оранжевый",
                "Абстрактный узор",
                "Ночной город",
                "Природа",
                "Темная тема",
                "Светлая тема"
        };
        fullPreviewText.setText(backgroundNames[currentBackground]);

        btnApply.setOnClickListener(v -> {
            fullScreenDialog.dismiss();
            Toast.makeText(requireContext(), "Фон применен", Toast.LENGTH_SHORT).show();
            broadcastBackgroundChanged(currentBackground);
        });

        btnBack.setOnClickListener(v -> {
            fullScreenDialog.dismiss();
            showBackgroundSelectionDialog();
        });

        if (fullPreview != null) {
            fullPreview.setOnClickListener(v -> fullScreenDialog.dismiss());
        }
        fullScreenDialog.show();
    }

    private void setPreviewBackground(ImageView imageView, int backgroundId) {
        if (imageView == null) return;

        switch (backgroundId) {
            case 0:
                imageView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.background_default));
                break;
            case 1:
                imageView.setBackground(getGradientDrawable(new int[]{Color.parseColor("#1E3C72"), Color.parseColor("#2A5298")}));
                break;
            case 2:
                imageView.setBackground(getGradientDrawable(new int[]{Color.parseColor("#667eea"), Color.parseColor("#764ba2")}));
                break;
            case 3:
                imageView.setBackground(getGradientDrawable(new int[]{Color.parseColor("#11998e"), Color.parseColor("#38ef7d")}));
                break;
            case 4:
                imageView.setBackground(getGradientDrawable(new int[]{Color.parseColor("#ff9a00"), Color.parseColor("#ff5e00")}));
                break;
            case 5:
                imageView.setBackgroundResource(R.drawable.bg_abstract_pattern);
                break;
            case 6:
                imageView.setBackgroundResource(R.drawable.bg_night_city);
                break;
            case 7:
                imageView.setBackgroundResource(R.drawable.bg_nature);
                break;
            case 8:
                imageView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.background_dark));
                break;
            case 9:
                imageView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.background_light));
                break;
        }
    }

    private GradientDrawable getGradientDrawable(int[] colors) {
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
        gradient.setCornerRadius(0f);
        return gradient;
    }

    private void saveBackgroundPreference(int backgroundId) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(PREF_CHAT_BACKGROUND, backgroundId).apply();
    }

    private void updateBackgroundButtonPreview() {
        if (changeBackgroundButton == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int currentBackground = prefs.getInt(PREF_CHAT_BACKGROUND, 0);

        TextView buttonText = changeBackgroundButton.findViewById(R.id.button_text);
        ImageView buttonIcon = changeBackgroundButton.findViewById(R.id.button_icon);

        if (buttonText != null) {
            String[] backgroundNames = {
                    "Стандартный фон", "Синий градиент", "Фиолетовый градиент", "Зеленый градиент",
                    "Оранжевый градиент", "Абстрактный узор", "Ночной город", "Природа",
                    "Темная тема", "Светлая тема"
            };
            String currentBackgroundName = currentBackground < backgroundNames.length ?
                    backgroundNames[currentBackground] : "Стандартный фон";
            buttonText.setText(currentBackgroundName);
        }

        if (buttonIcon != null) {
            int iconResource = R.drawable.ic_background_default;
            switch (currentBackground) {
                case 1:
                case 2:
                case 3:
                case 4:
                    iconResource = R.drawable.bg_gradient;
                    break;
                case 5:
                case 6:
                case 7:
                    iconResource = R.drawable.bg_nature;
                    break;
                case 8:
                    iconResource = R.drawable.bg_abstract_pattern;
                    break;
                case 9:
                    iconResource = R.drawable.bg_night_city;
                    break;
            }
            buttonIcon.setImageResource(iconResource);
        }
    }

    private void broadcastBackgroundChanged(int backgroundId) {
        Intent intent = new Intent("CHAT_BACKGROUND_CHANGED");
        intent.putExtra("background_id", backgroundId);
        requireContext().sendBroadcast(intent);
    }

    // THEME AND NOTIFICATIONS METHODS
    private void saveThemePreference(boolean isDarkTheme) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_DARK_THEME, isDarkTheme).apply();
    }

    private void saveNotificationsPreference(boolean notificationsEnabled) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_NOTIFICATIONS_ENABLED, notificationsEnabled).apply();
    }

    private void applyTheme(boolean isDarkTheme) {
        if (isDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
        requireActivity().recreate();
    }

    private void applyNotificationsSetting(boolean notificationsEnabled) {
        if (notificationsEnabled) {
            enableNotifications();
            Toast.makeText(requireContext(), "Уведомления включены", Toast.LENGTH_SHORT).show();
        } else {
            disableNotifications();
            Toast.makeText(requireContext(), "Уведомления отключены", Toast.LENGTH_SHORT).show();
        }
    }

    private void enableNotifications() {
        broadcastNotificationsState(true);
    }

    private void disableNotifications() {
        NotificationManager notificationManager = (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }
        broadcastNotificationsState(false);
    }

    private void broadcastNotificationsState(boolean enabled) {
        Intent intent = new Intent("NOTIFICATIONS_STATE_CHANGED");
        intent.putExtra("notifications_enabled", enabled);
        requireContext().sendBroadcast(intent);
    }

    // PROFILE METHODS
    private void loadUserProfile() {
        String accessToken = TokenManager.getInstance(requireContext()).getToken();
        if (accessToken == null || userId == null) {
            if (userNameTextView != null) {
                userNameTextView.setText("Не авторизован");
            }
            return;
        }

        String url = "https://api.vk.com/method/users.get" +
                "?user_ids=" + userId +
                "&access_token=" + accessToken +
                "&fields=photo_100" +
                "&v=5.131";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "KateMobileAndroid/56 lite-447 (Android 6.0; SDK 23; x86; Google Android SDK built for x86; en)")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    if (userNameTextView != null) {
                        userNameTextView.setText("Ошибка соединения");
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    if (json.has("error")) {
                        JSONObject error = json.getJSONObject("error");
                        String errorMsg = error.getString("error_msg");
                        requireActivity().runOnUiThread(() -> {
                            if (userNameTextView != null) {
                                userNameTextView.setText("Ошибка: " + errorMsg);
                            }
                        });
                        return;
                    }

                    JSONArray users = json.getJSONArray("response");
                    if (users.length() == 0) {
                        requireActivity().runOnUiThread(() -> {
                            if (userNameTextView != null) {
                                userNameTextView.setText("Пользователь не найден");
                            }
                        });
                        return;
                    }

                    JSONObject user = users.getJSONObject(0);
                    userFirstName = user.getString("first_name");
                    userLastName = user.getString("last_name");
                    String fullName = userFirstName + " " + userLastName;
                    String photoUrl = user.optString("photo_100", null);

                    requireActivity().runOnUiThread(() -> {
                        if (userNameTextView != null) {
                            userNameTextView.setText(fullName);
                        }
                        // Загрузка аватара с помощью Picasso
                        if (photoUrl != null && !photoUrl.isEmpty() && userAvatarImageView != null) {
                            Picasso.get()
                                    .load(photoUrl)
                                    .placeholder(R.drawable.default_avatar)
                                    .error(R.drawable.default_avatar)
                                    .into(userAvatarImageView);
                        }
                    });
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        if (userNameTextView != null) {
                            userNameTextView.setText("Ошибка загрузки");
                        }
                    });
                }
            }
        });
    }

    // HELP AND ABOUT METHODS
    private void showHelpDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Помощь")
                .setMessage("Если у вас возникли проблемы с приложением:\n\n" +
                        "• Проверьте подключение к интернету\n" +
                        "• Убедитесь, что введены правильные данные VK\n" +
                        "• Перезапустите приложение\n" +
                        "• При необходимости очистите кэш приложения\n\n" +
                        "Для дополнительной помощи свяжитесь с поддержкой.")
                .setPositiveButton("OK", null)
                .setNeutralButton("Связь с поддержкой", (dialog, which) -> {
                    contactSupport();
                })
                .show();
    }

    private void showAboutDialog() {
        String versionName = "1.0.0";
        try {
            versionName = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0)
                    .versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("О приложении")
                .setMessage("VK Messenger\n\n" +
                        "Версия: " + versionName + "\n" +
                        "© 2024 VK Messenger Team\n\n" +
                        "Неофициальное приложение для общения ВКонтакте")
                .setPositiveButton("OK", null)
                .setNeutralButton("Лицензия", (dialog, which) -> {
                    showLicenseDialog();
                })
                .show();
    }

    private void showLicenseDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Лицензионное соглашение")
                .setMessage("Это приложение является неофициальным клиентом VK и создано в образовательных целях.\n\n" +
                        "Все права на торговые марки VK принадлежат их владельцам.\n\n" +
                        "Приложение использует публичное API VK в соответствии с условиями использования.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void contactSupport() {
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:mrdevslis@example.com"));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Помощь с Scrippy Messenger");

        try {
            startActivity(Intent.createChooser(emailIntent, "Отправить email"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "Email приложение не найдено", Toast.LENGTH_SHORT).show();
        }
    }

    // EQUALIZER METHODS
    private void openSystemEqualizer() {
        try {
            Intent equalizerIntent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
            equalizerIntent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
            equalizerIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, requireContext().getPackageName());

            PackageManager pm = requireContext().getPackageManager();
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(equalizerIntent, 0);

            if (resolveInfos.isEmpty()) {
                showEqualizerNotAvailableToast();
            } else {
                startActivity(equalizerIntent);
            }

        } catch (ActivityNotFoundException e) {
            Log.e("SettingsFragment", "Equalizer activity not found", e);
            showEqualizerNotAvailableToast();
        } catch (Exception e) {
            Log.e("SettingsFragment", "Error opening equalizer", e);
            showEqualizerNotAvailableToast();
        }
    }

    private void showEqualizerNotAvailableToast() {
        Toast.makeText(requireContext(),
                "Системный эквалайзер не доступен на этом устройстве",
                Toast.LENGTH_LONG).show();
    }

    // LOGOUT METHODS
    public static void logout(Context context) {
        PinManager.resetAuthentication(context);
        PinManager.clearAllPinData(context);

        SharedPreferences prefs = context.getSharedPreferences("VK", Context.MODE_PRIVATE);
        prefs.edit()
                .remove("access_token")
                .remove("user_id")
                .remove("full_name")
                .apply();

        clearCookies(context);
        clearWebViewData(context);
    }

    private static void clearCookies(Context context) {
        CookieManager cookieManager = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(null);
            cookieManager.flush();
        } else {
            CookieSyncManager.createInstance(context);
            cookieManager.removeAllCookie();
            CookieSyncManager.getInstance().sync();
        }
    }

    private static void clearWebViewData(Context context) {
        try {
            context.deleteDatabase("webview.db");
            context.deleteDatabase("webviewCache.db");
            clearAppCache(context);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                WebStorage.getInstance().deleteAllData();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void clearAppCache(Context context) {
        try {
            File cacheDir = context.getCacheDir();
            if (cacheDir != null && cacheDir.isDirectory()) {
                deleteDir(cacheDir);
            }

            File webViewCacheDir = new File(context.getCacheDir(), "webview");
            if (webViewCacheDir.exists() && webViewCacheDir.isDirectory()) {
                deleteDir(webViewCacheDir);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
        }
        return dir != null && dir.delete();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(requireActivity(), VKAuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    // UPDATE METHODS
    @Override
    public void onResume() {
        super.onResume();
        if (userId != null) {
            loadUserProfile();
        }
        updateAllSwitchesState();
        updateBackgroundButtonPreview();
    }

    private void updateAllSwitchesState() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        if (themeSwitch != null) {
            themeSwitch.setChecked(prefs.getBoolean(PREF_DARK_THEME, false));
        }
        if (notificationsSwitch != null) {
            notificationsSwitch.setChecked(prefs.getBoolean(PREF_NOTIFICATIONS_ENABLED, true));
        }
        if (markAsReadSwitch != null) {
            markAsReadSwitch.setChecked(prefs.getBoolean(PREF_MARK_AS_READ, true));
        }
        if (alwaysOnlineSwitch != null) {
            alwaysOnlineSwitch.setChecked(prefs.getBoolean(PREF_ALWAYS_ONLINE, false));
        }
        if (soundSwitch != null) {
            soundSwitch.setChecked(prefs.getBoolean(PREF_SOUND_ENABLED, true));
        }
        if (vibrationSwitch != null) {
            vibrationSwitch.setChecked(prefs.getBoolean(PREF_VIBRATION_ENABLED, true));
        }
        // ИЗМЕНЕНИЕ: по умолчанию false (выключено)
        if (stickersAsStickersSwitch != null) {
            stickersAsStickersSwitch.setChecked(prefs.getBoolean(PREF_SEND_STICKERS_AS_STICKERS, false));
        }

        updatePinSwitchesState();
    }

    private void updatePinSwitchesState() {
        boolean pinEnabled = PinManager.isPinEnabled(requireContext());
        boolean fingerprintEnabled = PinManager.isFingerprintEnabled(requireContext());

        if (pinSwitch != null) {
            pinSwitch.setChecked(pinEnabled);
        }
        if (fingerprintSwitch != null) {
            fingerprintSwitch.setChecked(fingerprintEnabled);
            fingerprintSwitch.setEnabled(pinEnabled);
        }
    }

    // STATIC METHODS FOR OTHER CLASSES
    public static boolean isMarkAsReadEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_MARK_AS_READ, true);
    }

    public static boolean isAlwaysOnlineEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_ALWAYS_ONLINE, false);
    }

    public static boolean areNotificationsEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_NOTIFICATIONS_ENABLED, true);
    }

    public static boolean isSoundEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_SOUND_ENABLED, true);
    }

    public static boolean isVibrationEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_VIBRATION_ENABLED, true);
    }

    // ИЗМЕНЕНИЕ: по умолчанию false (выключено)
    public static boolean isSendStickersAsStickersEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_SEND_STICKERS_AS_STICKERS, false);
    }

    public static int getCurrentChatBackground(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(PREF_CHAT_BACKGROUND, 0);
    }

    public static void applyChatBackground(Context context, View view) {
        int backgroundId = getCurrentChatBackground(context);
        applyBackgroundToView(context, view, backgroundId);
    }

    public static void applyBackgroundToView(Context context, View view, int backgroundId) {
        if (view == null) return;

        switch (backgroundId) {
            case 0:
                view.setBackgroundColor(ContextCompat.getColor(context, R.color.background_default));
                break;
            case 1:
                view.setBackground(createGradientDrawable(new int[]{Color.parseColor("#1E3C72"), Color.parseColor("#2A5298")}));
                break;
            case 2:
                view.setBackground(createGradientDrawable(new int[]{Color.parseColor("#667eea"), Color.parseColor("#764ba2")}));
                break;
            case 3:
                view.setBackground(createGradientDrawable(new int[]{Color.parseColor("#11998e"), Color.parseColor("#38ef7d")}));
                break;
            case 4:
                view.setBackground(createGradientDrawable(new int[]{Color.parseColor("#ff9a00"), Color.parseColor("#ff5e00")}));
                break;
            case 5:
                view.setBackgroundResource(R.drawable.bg_abstract_pattern);
                break;
            case 6:
                view.setBackgroundResource(R.drawable.bg_night_city);
                break;
            case 7:
                view.setBackgroundResource(R.drawable.bg_nature);
                break;
            case 8:
                view.setBackgroundColor(ContextCompat.getColor(context, R.color.background_dark));
                break;
            case 9:
                view.setBackgroundColor(ContextCompat.getColor(context, R.color.background_light));
                break;
        }
    }

    private static GradientDrawable createGradientDrawable(int[] colors) {
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
        gradient.setCornerRadius(0f);
        return gradient;
    }
}