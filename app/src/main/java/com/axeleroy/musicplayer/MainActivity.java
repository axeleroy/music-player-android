package com.axeleroy.musicplayer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.AppCompatSeekBar;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    boolean isBinded = false;
    MediaPlaybackService mediaPlaybackService;
    ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mediaPlaybackService = ((MediaPlaybackService.IDBinder)service).getService();
            isBinded = true;
            initInfos(mediaPlaybackService.getFile());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBinded = false;
        }
    };
    BroadcastReceiver receiverElapsedTime;
    BroadcastReceiver receiverCompleted;

    ViewGroup rootView;
    ImageButton buttonPlayPause;
    ImageButton buttonStop;
    ImageView albumArt;
    TextView titleTextView;
    TextView artistTextView;
    TextView elapsedTimeTextView;
    TextView durationTextView;
    AppCompatSeekBar elapsedTimeSeekBar;
    FloatingActionButton fab;

    int elapsedTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        rootView = (ViewGroup) findViewById(android.R.id.content);

        // BroadcastReceiver permettant de mettre à jour le temps écoulé
        receiverElapsedTime = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                elapsedTime = intent.getIntExtra(MediaPlaybackService.MPS_MESSAGE, 0);
                updateElapsedTime(elapsedTime);
            }
        };

        // BroadcastReceiver remettant à zéro l'UI quand la lecture est terminée
        receiverCompleted = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                clearInfos();
            }
        };

        buttonPlayPause = (ImageButton) findViewById(R.id.imageButtonPlayPause);
        buttonStop = (ImageButton) findViewById(R.id.imageButtonStop);

        albumArt = (ImageView) findViewById(R.id.albumArt);
        titleTextView = (TextView) findViewById(R.id.textViewTitle);
        artistTextView = (TextView) findViewById(R.id.textViewArtist);
        elapsedTimeTextView = (TextView) findViewById(R.id.textViewElapsedTime);
        durationTextView = (TextView) findViewById(R.id.textViewDuration);

        elapsedTimeSeekBar = (AppCompatSeekBar) findViewById(R.id.seekBar);

        buttonPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int resId;
                if (mediaPlaybackService.isPlaying()) {
                    resId = R.drawable.ic_play_circle_outline_black_48dp;
                    mediaPlaybackService.pause();
                } else {
                    resId = R.drawable.ic_pause_circle_outline_black_48dp;
                    mediaPlaybackService.play();
                }
                buttonPlayPause.setImageResource(resId);
            }
        });
        // TODO: corriger le bouton lecture désactivé à la reprise
        buttonPlayPause.setEnabled(false);

        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlaybackService.stop();
                buttonPlayPause.setImageResource(R.drawable.ic_play_circle_outline_black_48dp);
                buttonPlayPause.setEnabled(false);
                clearInfos();
            }
        });

        elapsedTimeSeekBar.setEnabled(false);
        elapsedTimeSeekBar.setProgress(0);
        elapsedTimeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Lorsque l'utilisateur touche à la SeekBar
                if (fromUser)
                    mediaPlaybackService.seekTo(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Intent pour récupérer un morceau
                Intent intent = new Intent();
                intent.setType("audio/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Choose Track"), 1);
            }
        });
    }

    @Override
    protected void onResume() {
        // Le bindService() est dans le onResume afin de lier le service à l'activité en cas de
        // changement d'orientation ou de passage en arrière-plan
        getApplicationContext().bindService(new Intent(getApplicationContext(),
                MediaPlaybackService.class), connection, BIND_AUTO_CREATE);

        // Enregistrement des BroadcastReceiver à la reprise de l'activité
        LocalBroadcastManager.getInstance(this).registerReceiver(receiverElapsedTime,
                new IntentFilter(MediaPlaybackService.MPS_RESULT)
        );
        LocalBroadcastManager.getInstance(this).registerReceiver(receiverCompleted,
                new IntentFilter(MediaPlaybackService.MPS_COMPLETED)
        );

        super.onResume();
    }

    @Override
    protected void onPause() {
        // Désenregistrement des BroadcastReceiver à la mise en pause de l'activité
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiverElapsedTime);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiverCompleted);
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Si l'intent retourne bien un fichier
        if(resultCode==RESULT_OK)
        {
            // Récupération de l'URI du titre choisi
            Uri selectedtrack = data.getData();
            // Lancement de la lecture
            mediaPlaybackService.init(selectedtrack);
            // Initialisation des informations
            initInfos(selectedtrack);
        }
    }

    private String secondsToString(int time) {
        time = time / 1000;
        return String.format("%2d:%02d", time / 60, time % 60);
    }

    private void initInfos(Uri uri) {
        // TODO: temps écoulé remis à zéro lors de la rotation lorsque playback en pause
        updateElapsedTime(elapsedTime);

        if (uri != null) {
            // Récupération des metadatas du titre
            MediaMetadataRetriever mData = new MediaMetadataRetriever();
            mData.setDataSource(this, uri);

            int duration = Integer.parseInt(mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

            titleTextView.setText(mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
            artistTextView.setText(mData.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
            durationTextView.setText(secondsToString(duration));

            elapsedTimeSeekBar.setMax(duration);
            elapsedTimeSeekBar.setEnabled(true);

            try {
                // Récupération et affichage de la pochette
                byte art[] = mData.getEmbeddedPicture();
                Bitmap image = BitmapFactory.decodeByteArray(art, 0, art.length);
                albumArt.setImageBitmap(image);

                // Récupération des couleurs de la pochette avec Palette afin de les appliquer au thème
                Palette.from(image).generate(new Palette.PaletteAsyncListener() {
                    @Override
                    public void onGenerated(Palette palette) {
                        setColors(palette);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }

            if (mediaPlaybackService.isPlaying()) {
                buttonPlayPause.setEnabled(true);
                buttonPlayPause.setImageResource(R.drawable.ic_pause_circle_outline_black_48dp);
            }
        }
    }

    private void updateElapsedTime(int elapsedTime) {
        elapsedTimeSeekBar.setProgress(elapsedTime);
        elapsedTimeTextView.setText(secondsToString(elapsedTime));
    }

    private void clearInfos() {
        durationTextView.setText("");
        elapsedTimeTextView.setText("");
        titleTextView.setText("-");
        artistTextView.setText("-");
        elapsedTime = 0;
        elapsedTimeSeekBar.setEnabled(false);
        elapsedTimeSeekBar.setProgress(0);
        albumArt.setImageResource(R.drawable.ic_album_white_400_128dp);
        setColors(null);
    }

    private void setColors(Palette palette) {
        // Si une palette est passée, ses couleurs sont utilisées et appliquées,
        // sinon les couleurs par défaut sont utilisées
        int colorPrimaryDark = (palette != null) ? palette.getDarkVibrantColor(
                ContextCompat.getColor(getApplicationContext(), R.color.colorPrimaryDark)
            ) : ContextCompat.getColor(getApplicationContext(), R.color.colorPrimaryDark);
        int colorPrimary  = (palette != null) ? palette.getVibrantColor(
                ContextCompat.getColor(getApplicationContext(), R.color.colorPrimary)
        ) : ContextCompat.getColor(getApplicationContext(), R.color.colorPrimary);
        int colorAccent = (palette != null) ? palette.getLightVibrantColor(
                ContextCompat.getColor(getApplicationContext(), R.color.colorAccent)
        ) : ContextCompat.getColor(getApplicationContext(), R.color.colorAccent);


        // La coloration de la bar de notification ne fonctionne que sur Lollipop et suppérieurs
        if (Build.VERSION.SDK_INT >= 21) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(colorPrimaryDark);
        }

        rootView.setBackgroundColor(colorPrimary);

        buttonPlayPause.setColorFilter(colorAccent);
        buttonStop.setColorFilter(colorAccent);

        elapsedTimeSeekBar.getProgressDrawable().setColorFilter(colorAccent, PorterDuff.Mode.SRC_IN);
        elapsedTimeSeekBar.getThumb().setColorFilter(colorAccent, PorterDuff.Mode.SRC_IN);

        fab.setBackgroundTintList(ColorStateList.valueOf(colorAccent));
    }
}
