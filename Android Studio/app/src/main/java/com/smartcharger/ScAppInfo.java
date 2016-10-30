package com.smartcharger;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

/**
 * Created by Zim on 05/07/2015.
 */
public class ScAppInfo extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_info);
    }

    public void exitInfo(View v) {

        // Exit the info screen
        finish();
    }
}
