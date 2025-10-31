package ru.lisdevs.messenger.account;

import static android.content.Context.MODE_PRIVATE;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.utils.TokenManager;

public class BottomSheetVKProfileFragment extends BottomSheetDialogFragment {

    private ShapeableImageView qrCodeImageView;

    private String ownerId = "613752664"; // ваш ID
    private String accessToken; // получаете через TokenManager
    private String apiVersion = "5.131";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_vk_profile, container, false);

        qrCodeImageView = view.findViewById(R.id.qrCodeImageView);

        Button buttonqrCode = view.findViewById(R.id.copyButton);
        buttonqrCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               copyLink();
            }
        });

        Button buttonId = view.findViewById(R.id.copyId);
        buttonId.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               copyId();
            }
        });

        Button share = view.findViewById(R.id.share);
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareLinkAndQRCode();
            }
        });

        // Получите токен
        accessToken = TokenManager.getInstance(getContext()).getToken();

        // Запускаем асинхронную задачу
        new FetchUserDataTask().execute();

        return view;
    }

    private class FetchUserDataTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... voids) {
            try {
                String userId = getUserId(getContext());
                String urlStr = "https://api.vk.com/method/users.get?user_ids=" + userId +
                        "&access_token=" + accessToken +
                        "&v=" + apiVersion;

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder responseStrBuilder = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    responseStrBuilder.append(inputLine);
                }
                in.close();

                JSONObject responseJson = new JSONObject(responseStrBuilder.toString());
                JSONArray responseArray = responseJson.getJSONArray("response");
                if (responseArray.length() > 0) {
                    JSONObject userObject = responseArray.getJSONObject(0);
                    String screenName = userObject.optString("screen_name");
                    if (screenName != null && !screenName.isEmpty()) {
                        return "https://vk.com/" + screenName;
                    } else {
                        int id = userObject.getInt("id");
                        return "https://vk.com/id" + id;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null; // В случае ошибки
        }

        @Override
        protected void onPostExecute(String profileUrl) {
            if (profileUrl != null && qrCodeImageView != null) {
                generateQRCode(profileUrl);
            }
        }
    }

    private void generateQRCode(String data) {
        BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
        try {
            Bitmap bitmap = barcodeEncoder.encodeBitmap(data, BarcodeFormat.QR_CODE, 600, 600);
            qrCodeImageView.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private void copyLink() {
        String userProfileUrl = "https://vk.com/id" + getUserId(getContext());

        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("Юзер ID", userProfileUrl);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Сыллка скопирована", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyId() {
        String userId = "https://vk.com/id" + getUserId(getContext());

        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("Юзер ID", userId);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "ID скопирован", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareLinkAndQRCode() {
        String userId = "@id" + ownerId;
        String link = "https://yourapp.com/user/" + ownerId;

        // Создаем QR-код (если нужно)
        Bitmap qrBitmap = generateQRCodeBitmap(link);
        if (qrBitmap == null) {
            Toast.makeText(getContext(), "Не удалось сгенерировать QR-код", Toast.LENGTH_SHORT).show();
            return;
        }

        // Сохраняем QR-код во временный файл, чтобы его можно было отправить
        try {
            File cachePath = new File(getContext().getCacheDir(), "images");
            cachePath.mkdirs();
            File file = new File(cachePath, "qr_code.png");
            FileOutputStream stream = new FileOutputStream(file);
            qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            Uri contentUri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);

            // Создаем Intent для общего доступа
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Посмотрите мой профиль: " + link);
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Поделиться через"));

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Ошибка при подготовке файла", Toast.LENGTH_SHORT).show();
        }
    }

    // Метод для генерации QR-кода
    private Bitmap generateQRCodeBitmap(String text) {
        try {
            BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x=0; x<width; x++) {
                for (int y=0; y<height; y++) {
                    bmp.setPixel(x,y, bitMatrix.get(x,y) ? Color.BLUE : Color.WHITE);
                }
            }
            return bmp;
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getUserId(Context context) {
        return context.getSharedPreferences("VK", MODE_PRIVATE).getString("user_id", null);
    }

}
