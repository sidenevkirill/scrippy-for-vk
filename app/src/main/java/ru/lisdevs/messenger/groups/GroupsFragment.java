package ru.lisdevs.messenger.groups;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.friends.FriendsFragment;
import ru.lisdevs.messenger.model.Group;
import ru.lisdevs.messenger.utils.TokenManager;

public class GroupsFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView statusTextView;
    private List<Long> groupIds = new ArrayList<>();
    private List<Group> groups = new ArrayList<>();
    private TextView buttonLogout;
    private GroupAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Toolbar toolbar;
    private List<Group> allGroups = new ArrayList<>(); // полный список групп
    private SearchView searchView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_groups, container, false);

        recyclerView = view.findViewById(R.id.groupsRecyclerView);
        statusTextView = view.findViewById(R.id.statusTextView);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        toolbar = view.findViewById(R.id.toolbar);
        // Устанавливаем меню для Toolbar
        toolbar.inflateMenu(R.menu.menu_groups);

        // Обработка клика по пунктам меню
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_show_bottom_sheet) {
                showBottomSheet();
                return true;
            }
            return false;
        });

        // Настройка SearchView
        Menu menu = toolbar.getMenu();
        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            searchView = (SearchView) searchItem.getActionView();
            searchView.setQueryHint("Поиск групп");
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    filterGroups(query);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    filterGroups(newText);
                    return true;
                }
            });
        }

        // Обработка клика по пунктам меню
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_show_bottom_sheet) {
                showBottomSheet();
                return true;
            }
            return false;
        });

        toolbar.setNavigationOnClickListener(v -> {
            FriendsFragment friendsFragment = new FriendsFragment();

            // Выполняем замену текущего фрагмента на новый
            FragmentManager fragmentManager = getParentFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.replace(R.id.container, friendsFragment);
            transaction.addToBackStack(null); // добавляем в бэкстек для возврата
            transaction.commit();

        });

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        // Обработка свайпа вниз для обновления
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Запускаем загрузку данных заново
                new FetchGroupsTask().execute();
            }
        });

        new FetchGroupsTask().execute();

        return view;
    }

    private void filterGroups(String query) {
        if (query == null || query.trim().isEmpty()) {
            // Если строка пустая, показываем все группы
            adapter.updateData(allGroups);
            return;
        }

        String lowerCaseQuery = query.toLowerCase();

        List<Group> filteredList = new ArrayList<>();
        for (Group group : allGroups) {
            if (group.name.toLowerCase().contains(lowerCaseQuery)) {
                filteredList.add(group);
            }
        }
        adapter.updateData(filteredList);
    }

    private void unsubscribeFromAllGroups() {
        new UnsubscribeTask().execute();
    }

    // Загрузка групп
    private class FetchGroupsTask extends AsyncTask<Void, Void, List<Group>> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (statusTextView != null) {
                statusTextView.setText("Загрузка групп");
                statusTextView.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected List<Group> doInBackground(Void... voids) {
            groupIds.clear();
            groups.clear();


            String userId = getUserId(getContext());
            String accessToken = TokenManager.getInstance(getContext()).getToken();
            String apiVersion = "5.131";

            // Получение списка групп пользователя
            Uri uri = Uri.parse("https://api.vk.com/method/groups.get")
                    .buildUpon()
                    .appendQueryParameter("user_id", userId)
                    .appendQueryParameter("access_token", accessToken)
                    .appendQueryParameter("v", apiVersion)
                    .build();

            try {
                Log.d("VK", "Запрос: " + uri.toString());
                URL url = new URL(uri.toString());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode == HttpURLConnection.HTTP_OK)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                BufferedReader in = new BufferedReader(new InputStreamReader(is));
                StringBuilder responseStrBuilder = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    responseStrBuilder.append(inputLine);
                }
                in.close();

                String responseStr = responseStrBuilder.toString();
                Log.d("VK", "Ответ groups.get: " + responseStr);

                JSONObject jsonResponse = new JSONObject(responseStr);

                if (jsonResponse.has("error")) {
                    Log.e("VK", "API Error: " + jsonResponse.getJSONObject("error").toString());
                    return Collections.emptyList();
                }

                if (jsonResponse.has("response")) {
                    JSONObject responseObject = jsonResponse.getJSONObject("response");
                    if (responseObject.has("items")) {
                        JSONArray itemsArray = responseObject.getJSONArray("items");
                        for (int i = 0; i < itemsArray.length(); i++) {
                            Object itemObj = itemsArray.get(i);
                            long groupId;

                            if (itemObj instanceof Number) {
                                groupId = ((Number) itemObj).longValue();
                                groupIds.add(groupId);
                            } else if (itemObj instanceof String) {
                                try {
                                    groupId = Long.parseLong((String) itemObj);
                                    groupIds.add(groupId);
                                } catch (NumberFormatException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        Log.d("VK", "Получено групп: " + groupIds.toString());
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            // Получение информации о группах по их ID
            if (!groupIds.isEmpty()) {
                groups.addAll(fetchGroupDetails(groupIds));
            }

            return groups;
        }

        private List<Group> fetchGroupDetails(List<Long> ids) {
            List<Group> resultGroups = new ArrayList<>();
            try {
                StringBuilder idsParamBuilder = new StringBuilder();
                for (Long id : ids) {
                    idsParamBuilder.append(id).append(",");
                }
                String idsParam = idsParamBuilder.toString();
                if (idsParam.endsWith(",")) {
                    idsParam = idsParam.substring(0, idsParam.length() - 1);
                }

                String accessToken = TokenManager.getInstance(getContext()).getToken();
                String apiVersion = "5.131";

                Uri uri = Uri.parse("https://api.vk.com/method/groups.getById")
                        .buildUpon()
                        .appendQueryParameter("group_ids", idsParam)
                        .appendQueryParameter("access_token", accessToken)
                        .appendQueryParameter("v", apiVersion)
                        .build();

                Log.d("VK", "Запрос групп getById: " + uri.toString());

                URL url = new URL(uri.toString());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode == HttpURLConnection.HTTP_OK)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                BufferedReader in = new BufferedReader(new InputStreamReader(is));
                StringBuilder responseStrBuilder = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    responseStrBuilder.append(inputLine);
                }
                in.close();

                String responseStr = responseStrBuilder.toString();
                Log.d("VK", "Ответ groups.getById: " + responseStr);

                JSONObject jsonResponse = new JSONObject(responseStr);

                if (jsonResponse.has("error")) {
                    Log.e("VK", "API Error getById: " + jsonResponse.getJSONObject("error").toString());
                    return resultGroups;
                }

                if (jsonResponse.has("response")) {
                    JSONArray itemsArray = jsonResponse.getJSONArray("response");
                    for (int i = 0; i < itemsArray.length(); i++) {
                        JSONObject g = itemsArray.getJSONObject(i);
                        long id = g.optLong("id");
                        String name = g.optString("name");
                        resultGroups.add(new Group(id, name));
                    }
                    Log.d("VK", "Получено групп по ID: " + resultGroups.size());
                }

            } catch (Exception e) {
                e.printStackTrace();
                Log.e("VK", "Ошибка fetchGroupDetails: " + e.toString());
            }
            return resultGroups;
        }

        @Override
        protected void onPostExecute(List<Group> groupsResult) {
            super.onPostExecute(groupsResult);
            if (getContext() == null) return;

            if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }

            if (groupsResult.isEmpty()) {
                statusTextView.setText("Группы не найдены");
                statusTextView.setVisibility(View.VISIBLE);
                recyclerView.setAdapter(null);
                allGroups.clear();
                adapter = null;
            } else {
                statusTextView.setVisibility(View.GONE);
                allGroups.clear();
                allGroups.addAll(groupsResult);

                if (adapter == null) {
                    adapter = new GroupAdapter(allGroups);
                    recyclerView.setAdapter(adapter);

                    // Обработка клика по группе внутри адаптера как раньше
                    adapter.setOnGroupClickListener(group -> {
                        Fragment detailFragment = GroupDetailsFragment.newInstance(group.id, group.name);
                        getParentFragmentManager().beginTransaction()
                                .replace(R.id.container, detailFragment)
                                .addToBackStack(null)
                                .commit();
                    });
                } else {
                    adapter.updateData(allGroups); // обновляем данные при повторной загрузке
                }

                // После загрузки можно применить фильтр по текущему запросу, если он есть
                String currentQuery = searchView != null ? searchView.getQuery().toString() : "";
                filterGroups(currentQuery);
            }
        }

        private void unsubscribeFromAllGroups() {
            new UnsubscribeTask().execute();
        }

        private class UnsubscribeTask extends AsyncTask<Void, Void, Void> {

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Отписка завершена", Toast.LENGTH_SHORT).show();
                    // Обновляем список групп после отписки
                    new FetchGroupsTask().execute();
                }
            }

            @Override
            protected Void doInBackground(Void... voids) {

                String accessToken = TokenManager.getInstance(getContext()).getToken();
                String apiVersion = "5.131";

                for (Long groupId : groupIds) {
                    try {
                        Uri uri = Uri.parse("https://api.vk.com/method/groups.leave")
                                .buildUpon()
                                .appendQueryParameter("group_id", String.valueOf(groupId))
                                .appendQueryParameter("access_token", accessToken)
                                .appendQueryParameter("v", apiVersion)
                                .build();

                        URL url = new URL(uri.toString());
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST"); // лучше POST

                        int responseCode = conn.getResponseCode();

                        InputStream is = (responseCode == HttpURLConnection.HTTP_OK) ?
                                conn.getInputStream() :
                                conn.getErrorStream();

                        BufferedReader in = new BufferedReader(new InputStreamReader(is));
                        StringBuilder responseStrBuilder = new StringBuilder();

                        String line;
                        while ((line = in.readLine()) != null) {
                            responseStrBuilder.append(line);
                        }
                        in.close();

                        String respStr = responseStrBuilder.toString();
                        Log.d("VK", "Ответ groups.leave: " + respStr);

                        JSONObject jsonResp = new JSONObject(respStr);

                        if (jsonResp.has("response")) {
                            int res = jsonResp.optInt("response");
                            if (res == 1) {
                                Log.d("VK", "Успешно отписались от группы ID: " + groupId);
                            } else {
                                Log.w("VK", "Не удалось отписаться от группы ID: " + groupId);
                            }
                        } else if (jsonResp.has("error")) {
                            JSONObject errorObj = jsonResp.getJSONObject("error");
                            int errorCode = errorObj.optInt("error_code");
                            String errorMsg = errorObj.optString("error_msg");
                            Log.e("VK", "Ошибка при отписке: " + errorMsg + " (код:" + errorCode + ")");
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }
        }
    }

    private class UnsubscribeTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if(getContext()!=null){
                Toast.makeText(getContext(),"Отписка завершена",Toast.LENGTH_SHORT).show();
                // Обновляем список групп после отписки
                new FetchGroupsTask().execute();
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {

            String accessToken= TokenManager.getInstance(getContext()).getToken();
            String apiVersion="5.131";

            for(Long groupId : groupIds){
                try{
                    Uri uri= Uri.parse( "https://api.vk.com/method/groups.leave")
                            .buildUpon()
                            .appendQueryParameter( "group_id", String.valueOf(groupId))
                            .appendQueryParameter( "access_token", accessToken)
                            .appendQueryParameter( "v", apiVersion)
                            .build();

                    URL url= new URL(uri.toString());
                    HttpURLConnection conn= (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod( "POST"); // лучше POST

                    int responseCode= conn.getResponseCode();

                    InputStream is= (responseCode==HttpURLConnection.HTTP_OK)?
                            conn.getInputStream():
                            conn.getErrorStream();

                    BufferedReader in= new BufferedReader( new InputStreamReader(is));
                    StringBuilder responseStrBuilder= new StringBuilder();

                    String line;
                    while((line=in.readLine())!=null){
                        responseStrBuilder.append(line);
                    }
                    in.close();

                    String respStr= responseStrBuilder.toString();
                    Log.d( "VK","Ответ groups.leave: "+respStr);

                    JSONObject jsonResp= new JSONObject(respStr);

                    if(jsonResp.has( "response")){
                        int res= jsonResp.optInt( "response");
                        if(res==1){
                            Log.d( "VK","Успешно отписались от группы ID: "+groupId);
                        } else{
                            Log.w( "VK","Не удалось отписаться от группы ID: "+groupId);
                        }
                    } else if(jsonResp.has( "error")){
                        JSONObject errorObj= jsonResp.getJSONObject( "error");
                        int errorCode= errorObj.optInt( "error_code");
                        String errorMsg= errorObj.optString( "error_msg");
                        Log.e( "VK","Ошибка при отписке: "+errorMsg+" (код:"+errorCode+")");
                    }

                } catch(Exception e){
                    e.printStackTrace();
                }
            }
            return null;
        }
    }

    private void showBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(getContext());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_unsubscribe, null);
        bottomSheetDialog.setContentView(view);

        // Находим кнопку logout (предположим, что она есть в layout)
        Button buttonLogout = view.findViewById(R.id.btnSubscribeBottom);
        buttonLogout.setOnClickListener(v -> {
            unsubscribeFromAllGroups();
            bottomSheetDialog.dismiss(); // закрываем при logout
        });

        bottomSheetDialog.show();
    }

    public String getUserId(Context context) {
        return context.getSharedPreferences("VK", MODE_PRIVATE).getString("user_id", null);
    }
}