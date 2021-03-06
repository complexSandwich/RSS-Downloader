package rssDownloader;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.ForkJoinPool;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

public class Downloader implements Runnable{
	private String destination;
	private int NUM_CONCURRENT_DOWNLOADS;
	private ForkJoinPool workerCreator;
	private UpdateTableModel table;
	private HashMap<String, String> filesSources;

	public Downloader(String destination, int numParrallelDownloads, UpdateTableModel table, HashMap<String, String> filesSources){
		this.destination = destination;
		this.NUM_CONCURRENT_DOWNLOADS = numParrallelDownloads;
		this.table = table;
		this.filesSources = filesSources;
	}

	public void run(){
		workerCreator = new ForkJoinPool(NUM_CONCURRENT_DOWNLOADS);
		for(Entry<String, String> entry: filesSources.entrySet()){
			workerCreator.execute(
					new Worker(entry.getKey(), destination, entry.getValue(), table));
		}
		while(!workerCreator.isQuiescent());
		workerCreator.shutdownNow();
		JOptionPane.showMessageDialog(new JFrame(), "All downloads finished!");
	}

	private class Worker extends SwingWorker<Void, Void> {
		private final String fileURL, filename;
		private final Path fileDestination;
		private UpdateTableModel model;

		public Worker(final String filename, String fileDestination, String fileURL, UpdateTableModel model){
			this.fileURL = fileURL;
			this.fileDestination = Paths.get(fileDestination);
			this.filename = filename;
			this.model = model;

			addPropertyChangeListener(new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					if (evt.getPropertyName().equals("progress")) {
						Worker.this.model.updateProgress(filename, (int) evt.getNewValue());
					}
				}
			});
		}

		@Override
		public Void doInBackground() {
			if(Files.exists(fileDestination)&& Files.isDirectory(fileDestination)
					&& !Files.exists(Paths.get(destination + filename))){
				URL website;
				InputStream toGet = null;
				FileOutputStream file = null;
				try {
					model.updateStatus(filename, "Downloading");
					website = new URL(fileURL);
					long downloaded = 0;
					int fileSize = website.openConnection().getContentLength();
					model.updateFileSize(filename, fileSize / 1048576);
					int read;
					byte data[] = new byte[1024];
					toGet = new BufferedInputStream(website.openStream());
					file = new FileOutputStream(destination + filename);
					while((read = toGet.read(data)) != -1){
						downloaded += read;
						int progress = (int) Math.round(((double) downloaded / (double) fileSize) * 100d);
						model.updateProgress(filename, progress);
						file.write(data);
					}
				} catch(Exception ignore){
				} finally {
					model.updateProgress(filename, 100);
					model.updateStatus(filename, "Finished");
					try {
						if(toGet != null)
							toGet.close();
					} catch (IOException ignore){}
					finally{
						try {
							if(file != null)
								file.close();
						}catch (IOException ignore) {}
					}
				}
				return null;
			}
			model.updateFileSize(filename, "Unkown");
			model.updateProgress(filename, 100);
			model.updateStatus(filename, "Skipped");
			return null;
		}
	}
}

