package com.example.mylecteurofmp3;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;

    // --- Variables pour la connexion au MusicService ---
    private MusicService musicService;
    private boolean isBound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;

            // On donne la liste au service dès la connexion
            musicService.setPlaylist(musiquesFiltrees);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    public static class Musique {
        public String titre;
        public String cheminFichier;

        public Musique(String titre, String cheminFichier) {
            this.titre = titre;
            this.cheminFichier = cheminFichier;
        }

        @Override
        public String toString() {
            return titre;
        }
    }

    private boolean isLiked = false;
    private ImageButton btnPlayPause;
    private ArrayAdapter<Musique> adapter;
    private List<Musique> toutesLesMusiques;
    private List<Musique> musiquesFiltrees;

    private TextView tabBibliotheque, tabFolders, tabLiked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toutesLesMusiques = new ArrayList<>();
        musiquesFiltrees = new ArrayList<>();

        ImageView imgProfile = findViewById(R.id.imgProfile);
        imgProfile.setOnClickListener(v -> {
            Toast.makeText(this, "Changer de photo de profil (Bientôt disponible)", Toast.LENGTH_SHORT).show();
        });

        tabBibliotheque = findViewById(R.id.tabBibliotheque);
        tabFolders = findViewById(R.id.tabFolders);
        tabLiked = findViewById(R.id.tabLiked);

        tabBibliotheque.setOnClickListener(v -> activerOnglet(tabBibliotheque));
        tabFolders.setOnClickListener(v -> activerOnglet(tabFolders));
        tabLiked.setOnClickListener(v -> activerOnglet(tabLiked));

        ListView listView = findViewById(R.id.listViewMusics);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, musiquesFiltrees);
        listView.setAdapter(adapter);

        // --- Clic sur une musique de la liste ---
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (isBound && musicService != null) {
                // On délègue totalement la gestion de l'index au Service
                musicService.jouerMusiqueALindex(position);
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            }
        });

        SearchView searchView = findViewById(R.id.searchBar);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { return false; }

            @Override
            public boolean onQueryTextChange(String newText) {
                filtrerMusiques(newText);
                return true;
            }
        });

        btnPlayPause = findViewById(R.id.btnPlayPause);
        ImageButton btnPrev = findViewById(R.id.btnPrev);
        ImageButton btnNext = findViewById(R.id.btnNext);
        androidx.cardview.widget.CardView btnLikeCard = findViewById(R.id.btnLikeCard);

        // --- Bouton Play/Pause ---
        btnPlayPause.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                if (musicService.isPlaying()) {
                    musicService.pause();
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                } else {
                    // S'il n'y a pas de musique en cours, on lance la première par défaut
                    // S'il y en a une (en pause), resume() la relancera.
                    if (musicService.getCurrentPosition() == 0 && !musiquesFiltrees.isEmpty()) {
                        musicService.jouerMusiqueALindex(0);
                    } else {
                        musicService.resume();
                    }
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                }
            }
        });

        // --- Bouton Précédent ---
        btnPrev.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                // Le service gère seul la logique des 3 secondes et du changement d'index
                musicService.precedent();
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            }
        });

        // --- Bouton Suivant ---
        btnNext.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                // Le service gère seul le changement d'index
                musicService.suivant();
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            }
        });

        btnLikeCard.setOnClickListener(v -> {
            isLiked = !isLiked;
            if (isLiked) {
                btnLikeCard.setCardBackgroundColor(0xFF2196F3);
            } else {
                btnLikeCard.setCardBackgroundColor(0xFFE0E0E0);
            }
        });

        verifierPermissionsEtCharger();
    }

    // --- Cycle de vie pour lier et délier le Service ---
    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MusicService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    private void activerOnglet(TextView ongletActif) {
        resetOnglet(tabBibliotheque);
        resetOnglet(tabFolders);
        resetOnglet(tabLiked);
        ongletActif.setTextColor(0xFF000000);
        ongletActif.setTypeface(null, android.graphics.Typeface.BOLD);
    }

    private void resetOnglet(TextView onglet) {
        onglet.setTextColor(0xFF888888);
        onglet.setTypeface(null, android.graphics.Typeface.NORMAL);
    }

    private void chargerMusiquesDuTelephone() {
        toutesLesMusiques.clear();
        String[] projection = { MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String ordreTri = MediaStore.Audio.Media.DATE_ADDED + " DESC";

        Cursor cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null, ordreTri
        );

        if (cursor != null) {
            int indexTitre = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int indexChemin = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);

            while (cursor.moveToNext()) {
                toutesLesMusiques.add(new Musique(cursor.getString(indexTitre), cursor.getString(indexChemin)));
            }
            cursor.close();
        }

        musiquesFiltrees.clear();
        musiquesFiltrees.addAll(toutesLesMusiques);
        adapter.notifyDataSetChanged();
    }

    private void verifierPermissionsEtCharger() {
        String permissionRequise = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
                Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permissionRequise) == PackageManager.PERMISSION_GRANTED) {
            chargerMusiquesDuTelephone();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{permissionRequise}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            chargerMusiquesDuTelephone();
        }
    }

    private void filtrerMusiques(String requete) {
        musiquesFiltrees.clear();
        String requeteNettoyee = nettoyerTexte(requete);
        for (Musique musique : toutesLesMusiques) {
            if (nettoyerTexte(musique.titre).contains(requeteNettoyee)) {
                musiquesFiltrees.add(musique);
            }
        }
        adapter.notifyDataSetChanged();
        if (isBound && musicService != null) {
            musicService.setPlaylist(musiquesFiltrees);
        }
    }

    private String nettoyerTexte(String texte) {
        if (texte == null) return "";
        return texte.toLowerCase().replaceAll("[^a-z0-9\\p{L}]", "");
    }
}