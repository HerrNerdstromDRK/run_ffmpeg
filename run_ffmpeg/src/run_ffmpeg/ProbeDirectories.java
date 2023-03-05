package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

/**
 * Run ffprobe on each file in the probe directories array and record that information
 *  into the probe database.
 * @author Dan
 */
public class ProbeDirectories
{
	/// Setup the logging subsystem
	private transient Logger log = null ;
	private transient Common common = null ;

	private transient MoviesAndShowsMongoDB masMDB = null ;
	private transient MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_probe_directories.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_probe_directories.txt" ;
	//	private final String pathToFFPROBE = run_ffmpeg.pathToFFPROBE ;

	/// The directories to probe
	private final String[] directoriesToProbe = {
			"\\\\yoda\\MP4\\Movies",
			"\\\\yoda\\MP4_2\\Movies",
			"\\\\yoda\\MP4_3\\Movies",
			"\\\\yoda\\MP4_4\\Movies",
			"\\\\yoda\\MP4\\TV Shows",
			"\\\\yoda\\MP4_2\\TV Shows",
			"\\\\yoda\\MP4_3\\TV Shows",
			"\\\\yoda\\MP4_4\\TV Shows",
			"\\\\yoda\\MKV_Archive1\\Movies",
			"\\\\yoda\\MKV_Archive2\\Movies",
			"\\\\yoda\\MKV_Archive3\\Movies",
			"\\\\yoda\\MKV_Archive4\\Movies",
			"\\\\yoda\\MKV_Archive4\\TV Shows",
			"\\\\yoda\\MKV_Archive5\\Movies",
			"\\\\yoda\\MKV_Archive6\\Movies",
			"\\\\yoda\\MKV_Archive7\\Movies",
			"\\\\yoda\\MKV_Archive8\\Movies",
			"\\\\yoda\\MKV_Archive9\\Movies",
			"\\\\yoda\\MKV_Archive1\\TV Shows",
			"\\\\yoda\\MKV_Archive2\\TV Shows",
			"\\\\yoda\\MKV_Archive3\\TV Shows",
			"\\\\yoda\\MKV_Archive5\\TV Shows",
			"\\\\yoda\\MKV_Archive6\\TV Shows",
			"\\\\yoda\\MKV_Archive7\\TV Shows",
			"\\\\yoda\\MKV_Archive8\\TV Shows",
			"\\\\yoda\\MKV_Archive9\\TV Shows",
	} ;

	/// The extensions of the files to probe herein.
	private final String[] extensionsToProbe = {
			".mkv",
			".mp4"
	} ;

	public ProbeDirectories()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		masMDB = new MoviesAndShowsMongoDB() ;
		probeInfoCollection = masMDB.getProbeInfoCollection() ;
	}

	public ProbeDirectories( Logger log,
			Common common,
			MoviesAndShowsMongoDB masMDB,
			MongoCollection< FFmpegProbeResult > probeInfoCollection )
	{
		this.log = log ;
		this.common = common ;
		this.masMDB = masMDB ;
		this.probeInfoCollection = probeInfoCollection ;
	}

	public static void main(String[] args)
	{
		ProbeDirectories pd = new ProbeDirectories() ;
		pd.probeDirectoriesAndUpdateDB() ;
	}

	public void probeDirectoriesAndUpdateDB()
	{
		final long startTime = System.nanoTime() ;
		probeDirectoriesAndUpdateDB( directoriesToProbe, extensionsToProbe ) ;
		final long endTime = System.nanoTime() ;

		log.info( common.makeElapsedTimeString( startTime, endTime ) ) ;
	}

	public void probeDirectoriesAndUpdateDB( final String[] directories, final String[] extensions )
	{		
		log.info( "Probing directories: " + common.toString( directories ) ) ;

		// Walk through each directory
		for( String directoryToProbe : directories )
		{
			log.info( "Probing directory: " + directoryToProbe ) ;
			if( common.shouldStopExecution( stopFileName ) )
			{
				// Stop running
				log.info( "Shutting down due to presence of stop file" ) ;
				break ;
			}

			// Find files in this directory to probe
			List< File > filesToProbe = common.getFilesInDirectoryByExtension( directoryToProbe, extensions ) ;

			// Walk through each file in this directory
			for( File fileToProbe : filesToProbe )
			{
				if( common.shouldStopExecution( stopFileName ) )
				{
					// Stop running
					break ;
				}

				probeFileAndUpdateDB( fileToProbe ) ;
			} // for( fileToProbe )

			// Apparently rapid calls to the database creates a bunch of heap usage
			// Clear that here to prevent memory problems.
			System.gc() ;

		} // for( filesToProbe )

		log.info( "Shutdown complete" ) ;
	}

	public FFmpegProbeResult probeFileAndUpdateDB( File fileToProbe )
	{
		// Has the file already been probed?
		FFmpegProbeResult probeResult = fileAlreadyProbed( probeInfoCollection, fileToProbe ) ;
		if( (probeResult != null) && !needsRefresh( fileToProbe, probeResult ) )
		{
			// No need to probe again, continue to the next file.
			log.fine( "File already exists, skipping: " + fileToProbe.getAbsolutePath() ) ;
			return probeResult ;
		}
		// Post-condition: File does not currently exist in the database, or it does and it needs a refresh, or it's null

		if( probeResult != null )
		{
			// In the case it needs a refresh, just delete the old one and re-probe
			log.fine( "Deleting probeResult: " + probeResult ) ;
			probeInfoCollection.deleteOne( Filters.eq( "_id", probeResult._id ) ) ;
		}

		// File needs to a probe
		log.info( "Probing " + fileToProbe.getAbsolutePath() ) ;

		// Handle the special case that this is a missing file substitute
		if( fileToProbe.getName().contains( common.getMissingFilePreExtension() ) )
		{
			// Missing file. Do not probe directly
			log.fine( "This is a missing file: " + fileToProbe.getAbsolutePath() );
			probeResult = new FFmpegProbeResult() ;
			probeResult.setFileNameWithPath( fileToProbe.getAbsolutePath() ) ;
			probeResult.setFileNameWithoutPath( fileToProbe.getName() ) ;
			probeResult.setFileNameShort( Common.shortenFileName( fileToProbe.getAbsolutePath() ) ) ;
			probeResult.probeTime = fileToProbe.lastModified() + 1 ;
			probeResult.chapters = new ArrayList< FFmpegChapter >() ;
			probeResult.error = new FFmpegError() ;
			probeResult.format = new FFmpegFormat() ;
			probeResult.streams = new ArrayList< FFmpegStream >() ;
		}
		else
		{
			// Probe the file with ffprobe
			probeResult = common.ffprobeFile( fileToProbe, log ) ;
		}

		// Push the probe result into the database.
		probeInfoCollection.insertOne( probeResult ) ;
		
		return probeResult ;
	}

	/**
	 * Return true if the probe result needs to be updated in the database.
	 * This occurs when the file:
	 *  - Exists in the database (pre-condition to call this method).
	 *  - Size has changed
	 *  - Has been updated since last probe
	 * @param fileToProbe
	 * @param probeResult
	 * @return
	 */
	public boolean needsRefresh( File fileToProbe, FFmpegProbeResult probeResult )
	{
		// Check for the special case of a missing file.
		if( fileToProbe.getName().contains( common.getMissingFilePreExtension() ) )
		{
			// Special files never need a refresh.
			return false ;
		}

		if( fileToProbe.length() != probeResult.size )
		{
			// Size has changed
			return true ;
		}
		if( fileToProbe.lastModified() > probeResult.getLastModified() )
		{
			return true ;
		}
		return false ;
	}

	/**
	 * Return true if the given file has already been probed. False otherwise.
	 * @param probeInfoCollection
	 * @param fileToProbe
	 * @return
	 */
	public FFmpegProbeResult fileAlreadyProbed( MongoCollection< FFmpegProbeResult > probeInfoCollection, final File fileToProbe )
	{
		FFmpegProbeResult theProbeResult = null ;
		FindIterable< FFmpegProbeResult > findResult =
				probeInfoCollection.find( Filters.eq( "filename", fileToProbe.getAbsolutePath() ) ) ;

		Iterator< FFmpegProbeResult > findIterator = findResult.iterator() ;
		if( findIterator.hasNext() )
		{
			// Found the item in the database.
			theProbeResult = findIterator.next() ;
			//			out( "fileAlreadyProbed> Found FFmpegProbeResult by filename: " + fileToProbe.getAbsolutePath() ) ;
		}

		//		out( "fileAlreadyProbed> Unable to find FFmpegProbeResult by filename: " + fileToProbe.getAbsolutePath() ) ;
		return theProbeResult ;
	}

}
