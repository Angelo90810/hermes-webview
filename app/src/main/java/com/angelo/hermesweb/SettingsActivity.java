package com.angelo.hermesweb;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getWindow().setStatusBarColor(com.google.android.material.color.MaterialColors.getColor(
                findViewById(android.R.id.content),
                com.google.android.material.R.attr.colorSurface));
        setTitle("Configurações");

        EditText host = findViewById(R.id.host);
        EditText port = findViewById(R.id.port);
        EditText user = findViewById(R.id.user);
        EditText pass = findViewById(R.id.pass);
        Button save = findViewById(R.id.save);
        Button test = findViewById(R.id.test);
        TextView testResult = findViewById(R.id.testResult);

        Prefs p = Prefs.load(this);
        host.setText(p.host);
        port.setText(String.valueOf(p.port));
        user.setText(p.user);
        pass.setText(p.password);

        TextView version = findViewById(R.id.version);
        try {
            version.setText("v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (Exception ignored) {}

        save.setOnClickListener(v -> {
            String h = host.getText().toString().trim();
            String portS = port.getText().toString().trim();
            if (h.isEmpty() || portS.isEmpty()) {
                Toast.makeText(this, "Informe IP e porta", Toast.LENGTH_SHORT).show();
                return;
            }
            int portV;
            try {
                portV = Integer.parseInt(portS);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Porta inválida", Toast.LENGTH_SHORT).show();
                return;
            }
            Prefs.save(this, h, portV, user.getText().toString(), pass.getText().toString());
            setResult(RESULT_OK);
            finish();
        });

        test.setOnClickListener(v -> {
            String h = host.getText().toString().trim();
            String portS = port.getText().toString().trim();
            if (h.isEmpty() || portS.isEmpty()) {
                testResult.setText("Preencha IP e porta");
                return;
            }
            test.setEnabled(false);
            testResult.setText("Testando…");
            final String hostF = h, portF = portS;
            new Thread(() -> {
                String result;
                long t0 = System.currentTimeMillis();
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                            new java.net.URL("http://" + hostF + ":" + portF).openConnection();
                    c.setConnectTimeout(4000);
                    c.setReadTimeout(4000);
                    int code = c.getResponseCode();
                    long ms = System.currentTimeMillis() - t0;
                    result = "✓ Conectado em " + ms + " ms";
                } catch (Exception e) {
                    result = "✗ Sem resposta — confira IP, porta e rede (Wi-Fi ou Tailscale)";
                }
                String r = result;
                runOnUiThread(() -> {
                    testResult.setText(r);
                    test.setEnabled(true);
                });
            }, "conn-test").start();
        });
    }
}
