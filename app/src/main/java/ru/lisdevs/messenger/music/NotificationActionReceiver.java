package ru.lisdevs.messenger.music;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class NotificationActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if ("ACTION_PLAY_PAUSE".equals(action)) {
            // Тут можно реализовать паузу/игру
            // Например:
            // если воспроизведение идет - поставить на паузу и обновить уведомление
            // если поставлена на паузу - продолжить воспроизведение и обновить уведомление

            // Для простоты можно оставить пустым или реализовать по необходимости.

            // Пример:
            // MainActivity или сервис могут управлять MediaPlayer через статические методы или через Bound Service.

            Toast.makeText(context, "Кнопка Пауза/Играть нажата", Toast.LENGTH_SHORT).show();

            // Реализация зависит от архитектуры вашего приложения.

            // Например:
            // Если у вас есть статический экземпляр MediaPlayer в активити или сервисе,
            // вызовите соответствующий метод для переключения состояния.

            // Или отправьте локальный broadcast/intent для управления воспроизведением.

            // В этом примере оставим пустым.

        } else if ("ACTION_STOP".equals(action)) {
            // Остановить воспроизведение и закрыть уведомление
            // Аналогично — нужно реализовать управление MediaPlayer

            Toast.makeText(context, "Кнопка Стоп нажата", Toast.LENGTH_SHORT).show();

            // Например:
            // Если есть статический экземпляр MediaPlayer:
             /*
             if (MainActivity.mediaPlayer != null && MainActivity.mediaPlayer.isPlaying()) {
                 MainActivity.mediaPlayer.stop();
                 MainActivity.mediaPlayer.release();
                 MainActivity.mediaPlayer = null;

                 // Удаляем уведомление
                 NotificationManager notificationManager =
                         (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                 notificationManager.cancel(1);
             }
             */
        }
    }
}
