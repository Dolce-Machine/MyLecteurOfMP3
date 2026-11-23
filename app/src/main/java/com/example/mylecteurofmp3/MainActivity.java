package com.example.mylecteurofmp3;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String PREFS_NAME = "MyLecteurMP3Prefs";
    private static final String KEY_FAVORIS = "musiques_favorites";

    // --- Connexion au MusicService ---
    private MusicService musicService;
    private boolean isBound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;

            musicService.setPlaylist(musiquesFiltrees);
            synchroniserUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    public static class Musique {
        public String titre;
        public String cheminFichier;
        public Uri uri;

        public Musique(String titre, String cheminFichier, Uri uri) {
            this.titre = titre;
            this.cheminFichier = cheminFichier;
            this.uri = uri;
        }

        @Override
        public String toString() {
            return titre;
        }
    }

    private Set<String> favorisPaths = new HashSet<>();
    private boolean modeFavoris = false;
    private String rechercheActuelle = "";

    private CardView btnLikeCard;
    private ImageButton btnPlayPause; private ImageButton btnShuffle;
    private ArrayAdapter<Musique> adapter;
    private List<Musique> toutesLesMusiques;
    private List<Musique> musiquesFiltrees;

    private TextView tabBibliotheque, tabFolders, tabLiked;
    private TextView txtTitreActuel; private TextView txtTempsEcoule, txtDureeTotale;
    private SeekBar seekBar;
    private boolean isUserSeeking = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (isBound && musicService != null) {
                if (!isUserSeeking && seekBar != null) {
                    int duree = musicService.getDuration();
                    int position = musicService.getCurrentPosition();
                    if (duree > 0) {
                        seekBar.setMax(duree);
                        seekBar.setProgress(position);
                        if (txtTempsEcoule != null) txtTempsEcoule.setText(formaterTemps(position));
                        if (txtDureeTotale != null) txtDureeTotale.setText(formaterTemps(duree));
                    }
                }
                synchroniserUI();
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toutesLesMusiques = new ArrayList<>();
        musiquesFiltrees = new ArrayList<>();

        // 1. Charger les favoris sauvegardés dès le démarrage
        chargerFavoris();

        ImageView imgProfile = findViewById(R.id.imgProfile);
        imgProfile.setOnClickListener(v ->
                Toast.makeText(this, "Changer de photo de profil (Bientôt disponible)", Toast.LENGTH_SHORT).show()
        );
        btnShuffle = findViewById(R.id.btnShuffle);

        btnShuffle.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                musicService.toggleShuffle();
                synchroniserUI();
            }
        });
        ImageButton btnDelete = findViewById(R.id.btnDelete);
        btnDelete.setOnClickListener(v -> {
            if (isBound && musicService != null && musicService.getMusiqueActuelle() != null) {
                demanderSuppression(musicService.getMusiqueActuelle());
            } else {
                Toast.makeText(this, "Aucune musique en cours", Toast.LENGTH_SHORT).show();
            }
        });
        // 2. Onglets
        tabBibliotheque = findViewById(R.id.tabBibliotheque);
        tabFolders = findViewById(R.id.tabFolders);
        tabLiked = findViewById(R.id.tabLiked);

        tabBibliotheque.setOnClickListener(v -> {
            activerOnglet(tabBibliotheque);
            modeFavoris = false;
            appliquerFiltres();
        });

        tabFolders.setOnClickListener(v -> {
            activerOnglet(tabFolders);
            Toast.makeText(this, "Vue Dossiers à brancher", Toast.LENGTH_SHORT).show();
        });

        tabLiked.setOnClickListener(v -> {
            activerOnglet(tabLiked);
            modeFavoris = true;
            appliquerFiltres();
        });

        activerOnglet(tabBibliotheque);

        // 3. Liste des musiques
        ListView listView = findViewById(R.id.listViewMusics);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, musiquesFiltrees);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (isBound && musicService != null) {
                musicService.setPlaylist(musiquesFiltrees);
                musicService.jouerMusiqueALindex(position);
                synchroniserUI();
            }
        });

        // 4. Barre de recherche
        SearchView searchView = findViewById(R.id.searchBar);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { return false; }

            @Override
            public boolean onQueryTextChange(String newText) {
                rechercheActuelle = newText != null ? newText : "";
                appliquerFiltres();
                return true;
            }
        });

        // 5. Titre en cours, SeekBar et contrôles de lecture
        txtTitreActuel = findViewById(R.id.txtTitreActuel);
        seekBar = findViewById(R.id.seekBar);
        txtTempsEcoule = findViewById(R.id.txtTempsEcoule);
        txtDureeTotale = findViewById(R.id.txtDureeTotale);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        ImageButton btnPrev = findViewById(R.id.btnPrev);
        ImageButton btnNext = findViewById(R.id.btnNext);
        btnLikeCard = findViewById(R.id.btnLikeCard);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && txtTempsEcoule != null) {
                    txtTempsEcoule.setText(formaterTemps(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                if (isBound && musicService != null) {
                    musicService.seekTo(seekBar.getProgress());
                }
            }
        });

        btnPlayPause.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                if (musicService.isPlaying()) {
                    musicService.pause();
                } else {
                    if (musicService.getMusiqueActuelle() == null && !musiquesFiltrees.isEmpty()) {
                        musicService.setPlaylist(musiquesFiltrees);
                        musicService.jouerMusiqueALindex(0);
                    } else {
                        musicService.resume();
                    }
                }
                synchroniserUI();
            }
        });

        btnPrev.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                musicService.precedent();
                synchroniserUI();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                musicService.suivant();
                synchroniserUI();
            }
        });

        // 6. Bouton Favoris connecté aux SharedPreferences
        btnLikeCard.setOnClickListener(v -> {
            if (isBound && musicService != null && musicService.getMusiqueActuelle() != null) {
                String cheminActuel = musicService.getMusiqueActuelle().cheminFichier;
                if (favorisPaths.contains(cheminActuel)) {
                    favorisPaths.remove(cheminActuel);
                } else {
                    favorisPaths.add(cheminActuel);
                }
                sauvegarderFavoris();
                mettreAJourCouleurLike();

                if (modeFavoris) {
                    appliquerFiltres();
                }
            } else {
                Toast.makeText(this, "Lance une musique d'abord", Toast.LENGTH_SHORT).show();
            }
        });

        verifierPermissionsEtCharger();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MusicService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        synchroniserUI();
        handler.post(updateProgressRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateProgressRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    // --- Synchronisation UI (Play/Pause + Titre + Like) ---
    private void synchroniserUI() {
        if (isBound && musicService != null) {
            if (btnShuffle != null) {
                int couleurShuffle = musicService.isShuffle() ? 0xFF2196F3 : 0xFF888888;
                btnShuffle.setColorFilter(couleurShuffle);
            }

            btnPlayPause.setImageResource(
                    musicService.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play
            );

            if (txtTitreActuel != null) {
                Musique actuelle = musicService.getMusiqueActuelle();
                txtTitreActuel.setText(actuelle != null ? actuelle.titre : "Aucune musique");
            }
        }
        mettreAJourCouleurLike();
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
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String ordreTri = MediaStore.Audio.Media.DATE_ADDED + " DESC";

        Cursor cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null, ordreTri
        );

        if (cursor != null) {
            int indexId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int indexTitre = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int indexChemin = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(indexId);
                Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                toutesLesMusiques.add(new Musique(cursor.getString(indexTitre), cursor.getString(indexChemin), contentUri));
            }
            cursor.close();
        }

        appliquerFiltres();
    }
    private Musique musiqueASupprimer = null;

    private final ActivityResultLauncher<IntentSenderRequest> deleteLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (musiqueASupprimer != null) {
                        favorisPaths.remove(musiqueASupprimer.cheminFichier);
                        sauvegarderFavoris();
                        if (isBound && musicService != null) {
                            musicService.arreterSiActuelle(musiqueASupprimer.cheminFichier);
                        }
                        musiqueASupprimer = null;
                    }
                    Toast.makeText(this, "Musique supprimée définitivement", Toast.LENGTH_SHORT).show();
                    chargerMusiquesDuTelephone();
                    synchroniserUI();
                } else {
                    musiqueASupprimer = null;
                    Toast.makeText(this, "Suppression annulée", Toast.LENGTH_SHORT).show();
                }
            });
    private void demanderSuppression(Musique musique) {
        if (musique == null || musique.uri == null) return;
        musiqueASupprimer = musique;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PendingIntent pendingIntent = MediaStore.createDeleteRequest(
                    getContentResolver(),
                    Collections.singletonList(musique.uri)
            );
            IntentSenderRequest request = new IntentSenderRequest.Builder(pendingIntent.getIntentSender()).build();
            deleteLauncher.launch(request);
        } else {
            int deleted = getContentResolver().delete(musique.uri, null, null);
            if (deleted > 0) {
                favorisPaths.remove(musique.cheminFichier);
                sauvegarderFavoris();
                if (isBound && musicService != null) {
                    musicService.arreterSiActuelle(musique.cheminFichier);
                }
                Toast.makeText(this, "Musique supprimée définitivement", Toast.LENGTH_SHORT).show();
                chargerMusiquesDuTelephone();
                synchroniserUI();
            }
            musiqueASupprimer = null;
        }
    }

    // --- Permissions Audio + Notifications (Pixel 8 / Android 13+) ---
    private void verifierPermissionsEtCharger() {
        List<String> permissionsADemander = new ArrayList<>();

        String permAudio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permAudio) != PackageManager.PERMISSION_GRANTED) {
            permissionsADemander.add(permAudio);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsADemander.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (permissionsADemander.isEmpty()) {
            chargerMusiquesDuTelephone();
        } else {
            ActivityCompat.requestPermissions(this, permissionsADemander.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            String permAudio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? Manifest.permission.READ_MEDIA_AUDIO
                    : Manifest.permission.READ_EXTERNAL_STORAGE;

            if (ContextCompat.checkSelfPermission(this, permAudio) == PackageManager.PERMISSION_GRANTED) {
                chargerMusiquesDuTelephone();
            }
        }
    }

    // --- Filtrage combiné : Onglet (Tous / Favoris) + Recherche ---
    private void appliquerFiltres() {
        musiquesFiltrees.clear();
        String requeteNettoyee = nettoyerTexte(rechercheActuelle);

        for (Musique musique : toutesLesMusiques) {
            boolean matchFavori = !modeFavoris || favorisPaths.contains(musique.cheminFichier);
            boolean matchRecherche = requeteNettoyee.isEmpty() || nettoyerTexte(musique.titre).contains(requeteNettoyee);

            if (matchFavori && matchRecherche) {
                musiquesFiltrees.add(musique);
            }
        }

        adapter.notifyDataSetChanged();
        if (isBound && musicService != null) {
            musicService.setPlaylist(musiquesFiltrees);
        }
    }

    private void chargerFavoris() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet(KEY_FAVORIS, new HashSet<>());
        favorisPaths = new HashSet<>(saved);
    }

    private void sauvegarderFavoris() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putStringSet(KEY_FAVORIS, new HashSet<>(favorisPaths)).apply();
    }

    private void mettreAJourCouleurLike() {
        if (btnLikeCard == null) return;

        if (isBound && musicService != null && musicService.getMusiqueActuelle() != null) {
            String cheminActuel = musicService.getMusiqueActuelle().cheminFichier;
            if (favorisPaths.contains(cheminActuel)) {
                btnLikeCard.setCardBackgroundColor(0xFF2196F3); // Liké (Bleu)
                return;
            }
        }
        btnLikeCard.setCardBackgroundColor(0xFFE0E0E0); // Non liké (Gris)
    }
    private String formaterTemps(int millisecondes) {
        int totalSecondes = millisecondes / 1000;
        int minutes = totalSecondes / 60;
        int secondes = totalSecondes % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, secondes);
    }
    private String nettoyerTexte(String texte) {
        if (texte == null) return "";
        return texte.toLowerCase().replaceAll("[^a-z0-9\\p{L}]", "");
    }
}