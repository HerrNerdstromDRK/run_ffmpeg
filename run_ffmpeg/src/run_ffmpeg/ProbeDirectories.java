package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
	private static boolean testMode = true ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_probe_directories.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private static final String stopFileName = "C:\\Temp\\stop_probe_directories.txt" ;
	private static final String pathToFFPROBE = run_ffmpeg.pathToFFPROBE ;
	
	/// The directories to probe
	private static final String[] directoriesToProbe = {
			"\\\\yoda\\MKV_Archive1\\Movies",
			"\\\\yoda\\MKV_Archive2\\Movies",
			"\\\\yoda\\MKV_Archive3\\Movies",
			"\\\\yoda\\MKV_Archive4\\Movies",
			"\\\\yoda\\MKV_Archive5\\Movies",
			"\\\\yoda\\MKV_Archive6\\Movies",
			"\\\\yoda\\MKV_Archive7\\Movies",
			"\\\\yoda\\MKV_Archive8\\Movies",
			"\\\\yoda\\MKV_Archive9\\Movies",
	} ;
	
	/// The extensions of the files to probe herein.
	private static final String[] probeExtensions = {
			".mkv",
			".mp4"
	} ;
	
	public static void main(String[] args)
	{
		run_ffmpeg.testMode = testMode ;
		run_ffmpeg.openLogFile( logFileName ) ;
		
		out( "main> Probing directories: " + run_ffmpeg.toString( directoriesToProbe ) ) ;
		
		MoviesAndShowsMongoDB masMDB = new MoviesAndShowsMongoDB() ;
		MongoCollection< FFmpegProbeResult > probeInfoCollection = masMDB.getProbeInfoCollection() ;
		
		for( String directoryToProbe : directoriesToProbe )
		{
			out( "ProbeDirectories.main> Probing directory: " + directoryToProbe ) ;
			if( run_ffmpeg.stopExecution( stopFileName ) )
			{
				// Stop running
				out( "ProbeDirectories.main> Shutting down due to presence of stop file" ) ;
				break ;
			}
			List< File > filesToProbe = getFilesInDirectoryByExtension( directoryToProbe, probeExtensions ) ;
			for( File fileToProbe : filesToProbe )
			{
				if( run_ffmpeg.stopExecution( stopFileName ) )
				{
					// Stop running
					break ;
				}
				
				if( fileAlreadyProbed( probeInfoCollection, fileToProbe ) )
				{
					// TODO: && doesn't need a refresh
					// No need to probe again, continue to the next file.
					out( "ProbeDirectories.main> File already exists, skipping: " + fileToProbe.getAbsolutePath() ) ;
					continue ;
				}
				// Post-condition: File does not currently exist in the database

				// Add it
				FFmpegProbeResult theResult = ExtractPGSFromMKVs.ffprobeFile( fileToProbe ) ;
				
				// Push the probe result into the database.
				probeInfoCollection.insertOne( theResult ) ;
			} // for( fileToProbe )
			// Apparently rapid calls to the database creates a bunch of heap usage
			// Clear that here to prevent memory problems.
			System.gc() ;
		} // for( filesToProbe )

		out( "ProbeDirectories.main> Shutting down..." ) ;
		run_ffmpeg.closeLogFile() ;
		out( "ProbeDirectories.main> Shut down complete." ) ;
	}

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
	
	static synchronized void out( final String outputMe )
	{
		run_ffmpeg.out( outputMe ) ;
	}
	
	static synchronized void log( final String logMe )
	{
		run_ffmpeg.log( logMe ) ;
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