package com.example.east.scanner;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.dtr.zxing.activity.CaptureActivity;

public class MainActivity extends AppCompatActivity {
    private TextView tv;
    private Button button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv = (TextView) findViewById(R.id.textview_msg);
        button = (Button) findViewById(R.id.btn_scan);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setup();
            }
        });
    }

    private void setup(){
        Intent intent = new Intent(this, SecondActivity.class);
        intent.putExtra(CaptureActivity.KEY_INPUT_MODE, CaptureActivity.INPUT_MODE_QR);
        startActivityForResult(intent, 1111);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null) {
            if (data.hasExtra("sn")) {
                String sn = data.getStringExtra("sn");
                if (sn != null) {
                    TextView tv = (TextView) findViewById(R.id.textview_msg);
                    if (tv != null) {
                        tv.setText(sn);
                    }
                    Toast.makeText(this, sn, Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
