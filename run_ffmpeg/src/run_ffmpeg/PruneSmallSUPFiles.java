package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Purpose of this class is to remove all .sup files that are less than a certain
 * size because those files are invalid.
 * @author Dan
 */
public class PruneSmallSUPFiles
{
	/// Setup the logging subsystem
	private transient Logger log = null ;
	private transient Common common = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_prune_small_sup_files.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_prune_small_sup_files.txt" ;

	/// The minimum size of a .sup file to keep
	private final long minimalValidFileSize = 1000 ;

	/// The list of extensions to prune for size.
	private final String[] extensionsToPrune = { ".sup" } ;

	public static void main( String[] args )
	{
		PruneSmallSUPFiles pssf = new PruneSmallSUPFiles() ;
		pssf.run() ;
	}

	public PruneSmallSUPFiles()
	{
		log = Common.setupLogger( getLogFileName(), this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public void run()
	{
		common.setTestMode( true ) ;

		int numDeleted = 0 ;
		List< String > drivesAndFoldersToPrune = common.getAllMKVDrives() ;
		log.info( "Pruning: " + drivesAndFoldersToPrune.toString() ) ;

		for( String folderToPrune : drivesAndFoldersToPrune )
		{
			if( !shouldKeepRunning() )
			{
				log.info( "Shutting down in loop" ) ;
				break ;
			}
			numDeleted += pruneFolder( folderToPrune, getExtensionsToPrune() ) ;
		}

		log.info( "Shut down. Deleted " + numDeleted + " files." ) ;
	} // run()

	public int pruneFolder( final String folderToPrune )
	{
		return pruneFolder( folderToPrune, getExtensionsToPrune() ) ;
	}

	/**
	 * Deletes all files with the given extension(s)and returned the number of files deleted.
	 * @param folderToPrune
	 * @param extensionsToPrune
	 * @return
	 */
	public int pruneFolder( final String folderToPrune, final String[] extensionsToPrune )
	{
		int numDeleted = 0 ;
		
		List< File > filesToPrune = common.getFilesInDirectoryByExtension( folderToPrune, extensionsToPrune ) ;
		log.info( "Checking " + filesToPrune.size() + " files in folder " + folderToPrune ) ;

		for( File fileToPrune : filesToPrune )
		{
			if( fileToPrune.length() < getMinimalValidFileSize() )
			{
				// File too small.
				// Delete it
				log.info( "Deleting file " + fileToPrune.getAbsolutePath()
					+ ", size: " + fileToPrune.length()
					+ " less than minimum " + getMinimalValidFileSize() ) ;
				++numDeleted ;
				
				if( !common.getTestMode() )
				{
					fileToPrune.delete() ;
				}
			} // if( < length )
		} // for( fileToPrune )
		
		return numDeleted ;
	} // pruneFolder()

	public boolean shouldKeepRunning()
	{
		return !common.shouldStopExecution( stopFileName ) ;
	}

	public String[] getExtensionsToPrune() {
		return extensionsToPrune;
	}

	public String getLogFileName() {
		return logFileName;
	}

	public long getMinimalValidFileSize() {
		return minimalValidFileSize;
	}

	public String getStopFileName() {
		return stopFileName;
	}
}
