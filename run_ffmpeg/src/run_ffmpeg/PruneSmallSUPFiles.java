package run_ffmpeg;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

/**
 * Purpose of this class is to remove all .sup files that are less than a certain
 * size because those files are invalid.
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

	public PruneSmallSUPFiles()
	{
		log = Common.setupLogger( getLogFileName(), this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public static void main( String[] args )
	{
		PruneSmallSUPFiles pssf = new PruneSmallSUPFiles() ;
		pssf.run() ;
	}

	public void run()
	{
		common.setTestMode( true ) ;

		int numDeleted = 0 ;
		List< String > foldersToPrune = Common.getAllMediaFolders() ;
		log.info( "Pruning: " + foldersToPrune.toString() ) ;

		for( String folderToPrune : foldersToPrune )
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
	 * Creates empty srt files for all files with the given extension(s) that are too small to be useful srt files
	 * @param folderToPrune
	 * @param extensionsToPrune
	 * @return The number of empty srt files created.
	 */
	public int pruneFolder( final String folderToPrune, final String[] extensionsToPrune )
	{
		int numReplaced = 0 ;
		
		List< File > filesToPrune = common.getFilesInDirectoryByExtension( folderToPrune, extensionsToPrune ) ;
		log.info( "Checking " + filesToPrune.size() + " files in folder " + folderToPrune ) ;

		for( File fileToPrune : filesToPrune )
		{
			if( fileToPrune.length() < getMinimalValidFileSize() )
			{
				// File too small.
				// Build an empty srt file for this file.
				log.info( "Building empty srt file for " + fileToPrune.getAbsolutePath()
					+ ", size: " + fileToPrune.length()
					+ " less than minimum " + getMinimalValidFileSize() ) ;

				// Replace .sup with .srt
				final String srtFileName = Common.replaceExtension( fileToPrune.getName(), "srt" ) ;
				final File srtFile = new File( fileToPrune.getParentFile(), srtFileName ) ;
				
				SRTFileUtils srtFileUtils = new SRTFileUtils( log, common ) ;
				srtFileUtils.writeEmptySRTFile( srtFile ) ;
				
				++numReplaced ;
				
				if( !common.getTestMode() )
				{
					fileToPrune.delete() ;
				}
			} // if( < length )
		} // for( fileToPrune )
		
		return numReplaced ;
	} // pruneFolder()

	public boolean shouldKeepRunning()
	{
		return !common.shouldStopExecution( stopFileName ) ;
	}

	public String[] getExtensionsToPrune()
	{
		return extensionsToPrune ;
	}

	public String getLogFileName()
	{
		return logFileName ;
	}

	public long getMinimalValidFileSize()
	{
		return minimalValidFileSize ;
	}

	public String getStopFileName()
	{
		return stopFileName ;
	}
}
