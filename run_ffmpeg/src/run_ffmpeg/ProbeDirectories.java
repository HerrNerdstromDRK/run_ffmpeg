package run_ffmpeg;

import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
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
	/// Set testMode to true to prevent mutations
	/// Mostly used to interact with run_ffmpeg
	private static boolean testMode = true ;

	/// Setup the logging subsystem
	private Logger log = null ;
	
	/// File name to which to log activities for this application.
	private static final String logFileName = "log_probe_directories.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_probe_directories.txt" ;
//	private final String pathToFFPROBE = run_ffmpeg.pathToFFPROBE ;
	
	/// The directories to probe
	private final String[] _directoriesToProbe = {
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
	private final String[] _extensionsToProbe = {
			".mkv",
			".mp4"
	} ;
	
	public ProbeDirectories()
	{
		run_ffmpeg.testMode = testMode ;
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
	}
	
	public static void main(String[] args)
	{
		ProbeDirectories pd = new ProbeDirectories() ;
		pd.probeDirectoriesAndUpdateDB() ;
	}
	
	public void probeDirectoriesAndUpdateDB()
	{
		NumberFormat numFormat = NumberFormat.getInstance( new Locale( "en", "US" ) ) ;
		numFormat.setMaximumFractionDigits( 2 ) ;
		final long startTime = System.nanoTime() ;

		probeDirectoriesAndUpdateDB( _directoriesToProbe, _extensionsToProbe ) ;
		
		final long endTime = System.nanoTime() ;
		final double timeElapsedInSeconds = (endTime - startTime) / 1000000000.0 ;
    	log.info( "Total elapsed time: "
    			+ numFormat.format( timeElapsedInSeconds )
    			+ " seconds, "
    			+ numFormat.format( timeElapsedInSeconds / 60.0 )
    			+ " minutes" ) ;
	}
	
	public void probeDirectoriesAndUpdateDB( final String[] directories, final String[] extensions )
	{		
		log.info( "Probing directories: " + run_ffmpeg.toString( directories ) ) ;
		
		MoviesAndShowsMongoDB masMDB = new MoviesAndShowsMongoDB() ;
		MongoCollection< FFmpegProbeResult > probeInfoCollection = masMDB.getProbeInfoCollection() ;
		
		// Walk through each directory
		for( String directoryToProbe : directories )
		{
			log.info( "Probing directory: " + directoryToProbe ) ;
			if( run_ffmpeg.stopExecution( stopFileName ) )
			{
				// Stop running
				log.info( "Shutting down due to presence of stop file" ) ;
				break ;
			}
			
			// Find files in this directory to probe
			List< File > filesToProbe = getFilesInDirectoryByExtension( directoryToProbe, extensions ) ;
			
			// Walk through each file in this directory
			for( File fileToProbe : filesToProbe )
			{
				if( run_ffmpeg.stopExecution( stopFileName ) )
				{
					// Stop running
					break ;
				}
				
				// Has the directory already been probed?
				if( fileAlreadyProbed( probeInfoCollection, fileToProbe ) )
				{
					// TODO: && doesn't need a refresh
					// No need to probe again, continue to the next file.
					log.info( "File already exists, skipping: " + fileToProbe.getAbsolutePath() ) ;
					continue ;
				}
				// Post-condition: File does not currently exist in the database

				// Probe the file with ffprobe
				FFmpegProbeResult theResult = ExtractPGSFromMKVs.ffprobeFile( fileToProbe, log ) ;
				
				// Push the probe result into the database.
				probeInfoCollection.insertOne( theResult ) ;
			} // for( fileToProbe )
			
			// Apparently rapid calls to the database creates a bunch of heap usage
			// Clear that here to prevent memory problems.
			System.gc() ;
			
		} // for( filesToProbe )

		log.info( "Shutting down...complete" ) ;
	}

	/**
	 * Return true if the given file has already been probed. False otherwise.
	 * @param probeInfoCollection
	 * @param fileToProbe
	 * @return
	 */
	public static boolean fileAlreadyProbed( MongoCollection< FFmpegProbeResult > probeInfoCollection, final File fileToProbe )
	{
//		out( "fileAlreadyProbed> Looking for filename: " + fileToProbe.getAbsolutePath() ) ;
		FindIterable< FFmpegProbeResult > findResult =
				probeInfoCollection.find( Filters.eq( "filename", fileToProbe.getAbsolutePath() ) ) ;

		Iterator< FFmpegProbeResult > findIterator = findResult.iterator() ;
		if( findIterator.hasNext() )
		{
			// Found the item in the database.
//			out( "fileAlreadyProbed> Found FFmpegProbeResult by filename: " + fileToProbe.getAbsolutePath() ) ;
			return true ;
		}
		
//		out( "fileAlreadyProbed> Unable to find FFmpegProbeResult by filename: " + fileToProbe.getAbsolutePath() ) ;
		return false ;
	}
	
    public static List< File > getFilesInDirectoryByExtension( final String inputDirectory, final String[] inputExtensions )
    {
    	List< File > filesInDirectory = new ArrayList< >() ;
    	for( String extension : inputExtensions )
    	{
    		filesInDirectory.addAll( run_ffmpeg.getFilesInDirectoryWithExtension( inputDirectory, extension ) ) ;
    	}
    	return filesInDirectory ;
    }

	/**
	 * Sample code
	 */

	/*
	       for (String name : database.listCollectionNames()) { 
         System.out.println(name); 
      } 
	 */

}
