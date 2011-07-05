package edu.mit.media.hd.funf.configured;

import java.util.Map;

import org.json.JSONException;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import edu.mit.media.hd.funf.AndroidUtils;
import edu.mit.media.hd.funf.IOUtils;
import edu.mit.media.hd.funf.client.ProbeCommunicator;
import edu.mit.media.hd.funf.storage.DatabaseService;

public abstract class ConfigurationUpdaterService extends Service {
	public static final String TAG = ConfigurationUpdaterService.class.getName();
	
	@Override
	public IBinder onBind(Intent bindIntent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO: detect whether wifi is available, etc.
		try {
			FunfConfig config = getConfig();
			if (config == null) {
				Log.e(TAG, "Unable to get config");
			} else {
				FunfConfig oldConfig = FunfConfig.getFunfConfig(this);
				// TODO: re-enable (disabled for debugging)
				//if (!config.equals(oldConfig)) {

					if (oldConfig != null) {
						Log.i(TAG, "Removing old data requests");
						for (String probe : oldConfig.getDataRequests().keySet()) {
							ProbeCommunicator probeCommunicatior = new ProbeCommunicator(this, probe);
							probeCommunicatior.unregisterDataRequest();
						}
					}
					FunfConfig.setFunfConfig(this, config);
					Intent dbIntent = new Intent(this, getDatabaseServiceClass());
					bindService(dbIntent, new ServiceConnection() {
						@Override
						public void onServiceConnected(ComponentName name, IBinder service) {
							Log.i(TAG, "Reloading database configuration.");
							DatabaseService dbService = ((DatabaseService.LocalBinder)service).getService();
							dbService.reload();
							unbindService(this);
						}
						@Override
						public void onServiceDisconnected(ComponentName name) {
						}
					}, BIND_AUTO_CREATE);
					
					// Send data requests for all data requests
					for (Map.Entry<String,Bundle[]> entry : config.getDataRequests().entrySet()) {
						Log.i(TAG, "Registering data request for " + entry.getKey());
						ProbeCommunicator probeCommunicatior = new ProbeCommunicator(this, entry.getKey());
						probeCommunicatior.registerDataRequest(entry.getValue());
					}

				//}
			}
		} catch (JSONException e) {
			Log.e(TAG, e.getLocalizedMessage());
		}
		scheduleNextRun();
		stopSelf();
		return START_STICKY;
	}
	
	protected void scheduleNextRun() {
		FunfConfig config = FunfConfig.getFunfConfig(this);
		long updatePeriod = (config == null) ? 1 * 60 * 60 * 1000 : config.getUpdatePeriod();
		AndroidUtils.configureAlarm(this, getClass(), updatePeriod);
	}

	protected Class<? extends DatabaseService> getDatabaseServiceClass() {
		return ConfiguredDatabaseService.class;
	}
	
	protected FunfConfig getConfig() throws JSONException {
		String configJson = IOUtils.httpGet(getRemoteConfigUrl(), null);
		return (configJson == null) ? null : new FunfConfig(configJson);
	}
	
	protected abstract String getRemoteConfigUrl();

}