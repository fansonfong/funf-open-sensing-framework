package edu.mit.media.hd.funf.probe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.os.Bundle;
import android.util.Log;
import edu.mit.media.hd.funf.Utils;
import edu.mit.media.hd.funf.probe.CursorCell.AnyCell;
import edu.mit.media.hd.funf.probe.CursorCell.BooleanCell;
import edu.mit.media.hd.funf.probe.CursorCell.DoubleCell;
import edu.mit.media.hd.funf.probe.CursorCell.HashedCell;
import edu.mit.media.hd.funf.probe.CursorCell.IntCell;
import edu.mit.media.hd.funf.probe.CursorCell.LongCell;
import edu.mit.media.hd.funf.probe.CursorCell.StringCell;

public abstract class ContentProviderProbe extends Probe {

	protected Iterable<Bundle> mostRecentScan;
	private Thread onRunThread;
	
	@Override
	public Parameter[] getAvailableParameters() {
		return new Parameter[] {
			new Parameter(SystemParameter.PERIOD, 3600L)
		};
	}

	@Override
	public String[] getRequiredFeatures() {
		return null;
	}

	@Override
	protected void onEnable() {
		// Nothing
	}

	@Override
	protected void onDisable() {
		// Nothing
	}

	@Override
	protected void onRun(Bundle params) {
		if (onRunThread == null) {
			onRunThread = new Thread(new Runnable() {
				@Override
				public void run() {
					mostRecentScan = parseCursorResults();
					sendProbeData();
					onRunThread = null;
					stop();
				}
			});
			onRunThread.start();
		}
	}

	@Override
	protected void onStop() {
		if (onRunThread != null) {
			try {
				onRunThread.join(4000);
			} catch (InterruptedException e) {
				Log.e(TAG, "Didn't finish sending before probe was stopped");
			}
		}
		stopSelf();
	}

	@Override
	public void sendProbeData() {
		if (mostRecentScan != null ) {
			if (sendEachRowSeparately()) {
				for (Bundle data : mostRecentScan) {
					if (data != null) {
						sendProbeData(getTimestamp(data), new Bundle(), data);
					}
				}
			} else {
				Bundle data = new Bundle();
				ArrayList<Bundle> results = new ArrayList<Bundle>();
				for (Bundle item : mostRecentScan) {
					if (item != null) {
						results.add(item);
					}
				}
				data.putParcelableArrayList(getDataName(), results);
				sendProbeData(getTimestamp(results), new Bundle(), data);
			}
		}
	}
	
	protected boolean sendEachRowSeparately() {
		return false;
	}
	
	protected abstract String getDataName();
	
	protected abstract long getTimestamp(Bundle result);
	protected abstract long getTimestamp(List<Bundle> results);
	
	protected abstract Map<String,CursorCell<?>> getProjectionMap();
	
	protected abstract Cursor getCursor(String[] projection);
	
	protected Bundle parseDataBundle(Cursor cursor, String[] projection, Map<String,CursorCell<?>> projectionMap) {
		Bundle b = new Bundle();
    	for (String key : projection) {
    		CursorCell<?> cursorCell = projectionMap.get(key);
    		if (cursorCell != null) {
	    		Object value = cursorCell.getData(cursor, key);
	    		if (value != null) {
	    			Utils.putInBundle(b, key,value);
	    		}
    		}
    	}
    	return b;
	}
	
	private Iterable<Bundle> parseCursorResults() {
        return new Iterable<Bundle>() {
			@Override
			public Iterator<Bundle> iterator() {
				return new ContentProviderIterator();
			}
        	
        };
	}
	
	class ContentProviderIterator implements Iterator<Bundle> {

		private final Cursor c;
		private final String[] projection;
		private final Map<String, CursorCell<?>> projectionMap;
		private boolean brandNew; // Next has not been called
		
		public ContentProviderIterator() {
			this.projectionMap = getProjectionMap();
			this.projection = new String[projectionMap.size()];
			projectionMap.keySet().toArray(projection);
			this.c = getCursor(projection);
			int count = c.getCount();
			this.brandNew = true;
			Log.v(TAG, "cursor returned " + count +" result");
		}
		
		@Override
		public boolean hasNext() {
			Log.d(TAG, "Checking has next");
			boolean hasNext = brandNew ? c.moveToFirst() : !(c.isLast() || c.isAfterLast());
			if (!hasNext)
				c.close();
			return hasNext;
		}

		@Override
		public Bundle next() {
			if (brandNew) {
				c.moveToFirst();
				brandNew = false;
			} else {
				c.moveToNext();
			}
			Bundle dataBundle = null;
			try { 
				dataBundle =  parseDataBundle(c, projection, projectionMap);
			} catch (CursorIndexOutOfBoundsException e) {
				throw new NoSuchElementException();
			} finally {
				if (!hasNext()) {
					Log.d(TAG, "CLOSING cursor");
					c.close();
				}
			}
			return dataBundle;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
		
	}
	
	// Convenience methods that can be used to cache and reuse CursorCell objects
	
	protected static BooleanCell booleanCell() {
		return new BooleanCell();
	}
	
	protected static IntCell intCell() {
		return new IntCell();
	}
	
	protected static LongCell longCell() {
		return new LongCell();
	}
	
	protected static DoubleCell doubleCell() {
		return new DoubleCell();
	}
	
	protected static StringCell stringCell() {
		return new StringCell();
	}
	
	protected static AnyCell anyCell() {
		return new AnyCell();
	}
	
	protected CursorCell<String> hashedStringCell() {
		return hashedStringCell(this);
	}
	protected static CursorCell<String> hashedStringCell(Context context) {
		return new HashedCell(context, stringCell());
	}
	

}