package ru.lisdevs.messenger.search;


import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import ru.lisdevs.messenger.R;
import ru.lisdevs.messenger.friends.UserProfileFragment;
import ru.lisdevs.messenger.utils.TokenManager;

public class SearchFragment extends Fragment {

    private ListView listView;
    private ArrayList<HashMap<String, String>> results = new ArrayList<>();
    private SimpleAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        listView = view.findViewById(R.id.listView);
        adapter = new SimpleAdapter(getContext(), results, R.layout.list_item_search,
                new String[]{"name", "id"},
                new int[]{R.id.user, R.id.id});
        listView.setAdapter(adapter);

        // Обработка клика по элементу списка
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                HashMap<String, String> selectedItem = results.get(position);
                String userId = selectedItem.get("id");
                openUserProfile(userId);
            }
        });

        // Настройка SearchView
        SearchView searchView = view.findViewById(R.id.searchView);
        searchView.setQueryHint("Введите запрос");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // запуск поиска по API
                new VKSearchTask().execute(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // Можно реализовать автозаполнение или фильтрацию локально
                return false;
            }
        });

        return view;
    }


    // Асинхронная задача для поиска в VK
    private class VKSearchTask extends AsyncTask<String, Void, ArrayList<HashMap<String, String>>> {

        @Override
        protected ArrayList<HashMap<String, String>> doInBackground(String... params) {

            String accessToken = TokenManager.getInstance(getContext()).getToken();
            String apiVersion = "5.131";

            String query = params[0];
            String urlString = "https://api.vk.com/method/users.search"
                    + "?q=" + query
                    + "&count=20"
                    + "&access_token=" + accessToken
                    + "&v=" + apiVersion;

            ArrayList<HashMap<String, String>> resultList = new ArrayList<>();

            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder jsonBuilder = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }

                reader.close();

                JSONObject responseObject = new JSONObject(jsonBuilder.toString());
                JSONArray items = responseObject.getJSONObject("response").getJSONArray("items");

                for (int i=0; i<items.length(); i++) {
                    JSONObject userObj = items.getJSONObject(i);
                    String name = userObj.optString("first_name") + " " + userObj.optString("last_name");
                    String id = userObj.optString("id");

                    HashMap<String, String> map = new HashMap<>();
                    map.put("name", name);
                    map.put("id", id);
                    resultList.add(map);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return resultList;
        }

        @Override
        protected void onPostExecute(ArrayList<HashMap<String, String>> result) {
            super.onPostExecute(result);
            results.clear();
            results.addAll(result);
            adapter.notifyDataSetChanged();
        }
    }

    public String getUserId(Context context) {
        return context.getSharedPreferences("VK", MODE_PRIVATE).getString("user_id", null);
    }

    private void openUserProfile(String userId) {
        // Создаем экземпляр ProfileFragment
        UserProfileFragment profileFragment = new UserProfileFragment();

        // Передаем userId через аргументы
        Bundle args = new Bundle();
        args.putString("user_id", userId);
        profileFragment.setArguments(args);

        // Выполняем транзакцию для замены текущего фрагмента
        FragmentManager fragmentManager = getParentFragmentManager(); // или getFragmentManager() в старых версиях
        fragmentManager.beginTransaction()
                .replace(R.id.container, profileFragment) // контейнер для фрагментов в вашей активности
                .addToBackStack(null) // чтобы можно было вернуться назад
                .commit();
    }
}
