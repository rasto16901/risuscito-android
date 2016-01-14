package it.cammino.risuscito.slides;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.github.paolorotolo.appintro.AppIntro2;

import it.cammino.risuscito.MainActivity;
import it.cammino.risuscito.R;

public class IntroMain extends AppIntro2 {
    @Override
    public void init(Bundle savedInstanceState) {
        /*addSlide(DefaultSlide.newInstance(R.layout.intro_defaut_layout
                , R.string.help_new_menu_title
                , R.string.help_new_menu_desc
                , R.drawable.intro_drawer));*/
        addSlide(DefaultSlide.newInstance(R.layout.intro_defaut_layout
                , R.string.login_intro_title
                , R.string.login_intro_desc_0
                , R.drawable.intro_login_0));
        addSlide(DefaultSlide.newInstance(R.layout.intro_defaut_layout
                , R.string.login_intro_title
                , R.string.login_intro_desc_1
                , R.drawable.intro_login_1));
        addSlide(DefaultSlide.newInstance(R.layout.intro_defaut_layout
                , R.string.login_intro_title
                , R.string.login_intro_desc_2
                , R.drawable.intro_login_2));
        addSlide(DefaultSlide.newInstance(R.layout.intro_defaut_layout
                , R.string.login_intro_title
                , R.string.login_intro_desc_3
                , R.drawable.intro_login_3));
    }

    private void loadMainActivity(){
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    @Override
    public void onDonePressed() {
//        loadMainActivity();
        finish();
    }

    public void getStarted(View v){
//        loadMainActivity();
    }
}