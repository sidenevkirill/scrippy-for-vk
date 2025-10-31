package ru.lisdevs.messenger.auth;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.TimeUnit;

import ru.lisdevs.messenger.BaseActivity;
import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.VKAuthActivity;
import ru.lisdevs.messenger.utils.PinManager;
import ru.lisdevs.messenger.utils.TokenManager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.text.TextWatcher;
import android.text.Editable;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.TimeUnit;

public class PinLoginActivity extends AppCompatActivity {

    private static final String TAG = "PinLoginActivity";
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_TIME = 5 * 60 * 1000; // 5 минут

    private EditText[] pinFields;
    private TextView attemptsText;
    private Button confirmButton;
    private int currentAttempt = 0;
    private long lockoutUntil = 0;
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_login);

        initViews();
        setupPinFields();
        checkLockoutStatus();

        // Автофокус на первое поле
        if (pinFields.length > 0) {
            pinFields[0].requestFocus();
        }
    }

    private void initViews() {
        pinFields = new EditText[] {
                findViewById(R.id.pin_field_1),
                findViewById(R.id.pin_field_2),
                findViewById(R.id.pin_field_3),
                findViewById(R.id.pin_field_4)
        };

        attemptsText = findViewById(R.id.attempts_text);
        confirmButton = findViewById(R.id.confirm_button);

        confirmButton.setOnClickListener(v -> validatePin());

        // Кнопка выхода
        Button logoutButton = findViewById(R.id.logout_button);
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> logout());
        }
    }

    private void setupPinFields() {
        for (int i = 0; i < pinFields.length; i++) {
            final int currentIndex = i;
            final int nextIndex = i + 1;
            final int prevIndex = i - 1;

            pinFields[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1 && nextIndex < pinFields.length) {
                        pinFields[nextIndex].requestFocus();
                    }
                    updateConfirmButton();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            // Обработка удаления
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

    private void updateConfirmButton() {
        boolean allFieldsFilled = true;
        for (EditText field : pinFields) {
            if (field.getText().length() == 0) {
                allFieldsFilled = false;
                break;
            }
        }
        confirmButton.setEnabled(allFieldsFilled);
    }

    private void validatePin() {
        StringBuilder enteredPin = new StringBuilder();
        for (EditText field : pinFields) {
            enteredPin.append(field.getText().toString());
        }

        String savedPin = PinManager.getPin(this);
        boolean isPinCorrect = enteredPin.toString().equals(savedPin);

        if (isPinCorrect) {
            // Успешный вход - устанавливаем флаг успешной аутентификации с временем
            PinManager.resetAttempts(this);
            PinManager.setPinAuthenticated(this, true); // Здесь сохраняется время
            startMainActivity();
        } else {
            // Неверный PIN
            handleWrongPin();
        }
    }

    private void handleWrongPin() {
        currentAttempt = PinManager.getAttempts(this) + 1;
        PinManager.setAttempts(this, currentAttempt);

        if (currentAttempt >= MAX_ATTEMPTS) {
            // Блокировка
            lockoutUntil = System.currentTimeMillis() + LOCKOUT_TIME;
            PinManager.setLockoutUntil(this, lockoutUntil);
            showLockoutMessage();
        } else {
            // Показать оставшиеся попытки
            showWrongPinMessage();
        }

        // Очистить поля
        clearPinFields();
        if (pinFields.length > 0) {
            pinFields[0].requestFocus();
        }
    }

    private void checkLockoutStatus() {
        lockoutUntil = PinManager.getLockoutUntil(this);
        currentAttempt = PinManager.getAttempts(this);

        if (System.currentTimeMillis() < lockoutUntil) {
            showLockoutMessage();
        } else if (lockoutUntil > 0) {
            // Сброс после блокировки
            PinManager.resetAttempts(this);
            PinManager.setLockoutUntil(this, 0);
            currentAttempt = 0;
        }

        updateAttemptsText();
    }

    private void showLockoutMessage() {
        long remainingTime = lockoutUntil - System.currentTimeMillis();
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(remainingTime) -
                TimeUnit.MINUTES.toSeconds(minutes);

        String message = String.format("Приложение заблокировано\nПопробуйте через %d мин %d сек",
                minutes, seconds);

        attemptsText.setText(message);
        attemptsText.setTextColor(Color.RED);

        disableInput();

        // Запускаем таймер обратного отсчета
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(remainingTime, 1000) {
            public void onTick(long millisUntilFinished) {
                long min = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished);
                long sec = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) -
                        TimeUnit.MINUTES.toSeconds(min);

                String text = String.format("Приложение заблокировано\nПопробуйте через %d мин %d сек",
                        min, sec);
                attemptsText.setText(text);
            }

            public void onFinish() {
                PinManager.resetAttempts(PinLoginActivity.this);
                PinManager.setLockoutUntil(PinLoginActivity.this, 0);
                enableInput();
                updateAttemptsText();
            }
        }.start();
    }

    private void showWrongPinMessage() {
        int remainingAttempts = MAX_ATTEMPTS - currentAttempt;
        String message = "Неверный PIN-код\nОсталось попыток: " + remainingAttempts;

        attemptsText.setText(message);
        attemptsText.setTextColor(Color.RED);

        // Анимация тряски
        shakePinFields();
    }

    private void shakePinFields() {
        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
        for (EditText field : pinFields) {
            field.startAnimation(shake);
        }
    }

    private void updateAttemptsText() {
        if (currentAttempt > 0) {
            int remainingAttempts = MAX_ATTEMPTS - currentAttempt;
            attemptsText.setText("Осталось попыток: " + remainingAttempts);
            attemptsText.setTextColor(Color.BLACK);
        } else {
            attemptsText.setText("Введите PIN-код");
            attemptsText.setTextColor(Color.BLACK);
        }
    }

    private void disableInput() {
        for (EditText field : pinFields) {
            field.setEnabled(false);
        }
        confirmButton.setEnabled(false);
    }

    private void enableInput() {
        for (EditText field : pinFields) {
            field.setEnabled(true);
        }
        updateConfirmButton();
    }

    private void clearPinFields() {
        for (EditText field : pinFields) {
            field.setText("");
        }
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, BaseActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void logout() {
        // Полная очистка данных
        PinManager.clearAllPinData(this);
        // TokenManager.getInstance(this).clearTokens();

        Intent intent = new Intent(this, BaseActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        // Запрещаем возврат к авторизации
        moveTaskToBack(true);
    }
}