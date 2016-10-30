package com.smartcharger;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;


/**
 * Created by Zim on 05/07/2015.
 */
public class ScWarningInfo extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.err_info);
    }

    public void exitErrInfo(View v) {

        // Exit the error info screen
        finish();
    }

}
