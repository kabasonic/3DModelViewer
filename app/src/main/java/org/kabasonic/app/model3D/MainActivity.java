package org.kabasonic.app.model3D;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.kabasonic.app.model3D.view.MenuActivity;
import org.kabasonic.util.android.AndroidURLStreamHandlerFactory;

import java.net.URL;

/*
	1.Należy stworzyć aplikację będącą przeglądarką modeli 3D.
	Przeglądarka ma obsługiwać pliki z modelami w co najmniej
	jednym ogólnie przyjętym formacie (np. gltf, usdz, obj).
	Aplikacja powinna:
	- wyświetlać listę modeli pobierając je z bazy danych lub przestrzeni dyskowej.
	Lista powinna zawierać nazwę pliku oraz jego miniaturkę (generowaną w locie).
	- zawierać możliwość przejścia do podglądu poszczególnych modeli z listy
 */

public class MainActivity extends Activity {

    static {
        System.setProperty("java.protocol.handler.pkgs", "org.kabasonic.util.android");
        URL.setURLStreamHandlerFactory(new AndroidURLStreamHandlerFactory());
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Start Model activity.
		MainActivity.this.startActivity(new Intent(MainActivity.this.getApplicationContext(), MenuActivity.class));
		MainActivity.this.finish();
	}

}
