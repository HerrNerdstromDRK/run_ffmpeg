package run_ffmpeg;

import java.io.File ;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.text.WordUtils;

/**
 * Walks through all of the files given below and fixes capitalization.
 * That is, it will set the first letter of each word in the file name as capital.
 */
public class FixCapitalization
{
	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_fix_capitalization.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
//	private static final String stopFileName = "C:\\Temp\\stop_fix_capitalization.txt" ;

	public FixCapitalization()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public static void main( String[] args )
	{
		FixCapitalization fc = new FixCapitalization() ;
		fc.run() ;
	}

	public void run()
	{
		common.setTestMode( false ) ;

		final List< String > allDrives = new ArrayList< String >() ;
//		allDrives.add( "\\\\yoda\\MKV_Archive1\\Movies" ) ;
		allDrives.addAll( Common.getAllMediaFolders() ) ;

		// Look through each drive for all files ending in .mkv, .mp4, or .srt

		capitalizeFiles( allDrives, new String[] { ".mkv", ".mp4", ".srt" } ) ;
		capitalizeDirectories( allDrives ) ;
	}

	/**
	 * Walk through all folders in the given list of drives and capitalize the name of each folder.
	 * @param allDrives
	 */
	public void capitalizeDirectories( final List< String > theDrives )
	{
		int numRenamedDirectories = 0 ;
		final List< Path > directories = new ArrayList< Path >() ;
	
		try {
			for( String theDrive : theDrives )
			{
				directories.addAll( Files.walk( (new File( theDrive )).toPath() )
						.filter( Files::isDirectory )
						.collect( Collectors.toList() ) ) ;
			}
		} catch( Exception theException )
		{
			log.warning( "Error searching for directories: " + theException.toString() ) ;
		}
	
		log.info( "Found " + directories.size() + " folder(s): " + directories.toString() ) ;
	
		// Since the walk() method should return all directories, including those above the lowest-level
		//  subdirectories, I shouldn't need to use multiple getParent() calls on a single entry
		for( Path thePath : directories )
		{
			if( thePath.toString().contains( "RECYCLE.BIN" ) )
			{
				continue ;
			}
			numRenamedDirectories += capitalizeFile( thePath.toFile() ) ;
		}
		log.info( "Renamed " + numRenamedDirectories + " directory/directories" ) ;
	}

	public int capitalizeFile( File theFile )
	{
		int numRenamedFiles = 0 ;
		
		// Instead of using a search method to determine if the filename is invalid and then
		// a second method to fix it, let's just proceed to fix it inline here and check if
		// the filename has changed. If so, then rename the file.
		final String origFileName = theFile.getName() ;
		StringTokenizer tokens = new StringTokenizer( origFileName, " " ) ;
	
		boolean foundMisCapitalizedWord = false ;
		while( tokens.hasMoreTokens() && !foundMisCapitalizedWord )
		{
			String theToken = tokens.nextToken() ;
			if( '(' == theToken.charAt( 0 ) )
			{
				// Likely the opening parantheses for a movie date
				// For example, "X-Men The Last Stand (2006).6.srt"
				// Skip it
				continue ;
			}
	
			if( '[' == theToken.charAt( 0 ) )
			{
				// Opening for something like "[Extended]" or "[Directors Cut]"
				// Skip it
				continue ;
			}
	
			if( '-' == theToken.charAt( 0 ) )
			{
				// Each TV show has several '-'s -- skip them.
				// Skip it
				continue ;
			}
	
			if( Character.isDigit( theToken.charAt( 0 ) ) )
			{
				// Starts with a number
				// Skip it
				continue ;
			}				
	
			if( !Character.isUpperCase( theToken.charAt( 0 ) ) )
			{
				// Found a miscapitalized word
				log.fine( "Found miscapitalized word: " + theToken ) ;
				foundMisCapitalizedWord = true ;
				break ;
			}
		}
	
		if( foundMisCapitalizedWord )
		{
			++numRenamedFiles ;
	
			// Since windows filenames are case insensitive, trying to rename files based solely on
			//  case will generate an error.
			// Therefore, I will rename the original file to an intermediate file, then rename that
			//  file to the final file name.				
			final String finalFileName = WordUtils.capitalize( origFileName ) ;
			File finalFile = new File( theFile.getParent(), finalFileName ) ;
	
			final String tempFileName = "_" + finalFileName ;
			File tempFile = new File( theFile.getParent(), tempFileName ) ;
	
			log.info( "Renaming: " + theFile.getAbsolutePath() + "->" + tempFile.getAbsoluteFile() + "->" + finalFile.getAbsolutePath() ) ;
			if( !common.getTestMode() )
			{
				try
				{
					theFile.renameTo( tempFile ) ;
				}
				catch( Exception theException )
				{
					log.warning( "Error moving file: " + theFile.getAbsolutePath() + "->" + tempFile.getAbsolutePath() + ": " + theException.toString() ) ;
				}
				try
				{
					tempFile.renameTo( finalFile ) ;
				}
				catch( Exception theException )
				{
					log.warning( "Error moving file: " + tempFile.getAbsolutePath() + "->" + finalFile.getAbsolutePath() + ": " + theException.toString() ) ;
				}
			} // if( testMode )
		} // if( foundCapitalizedWord )
		return numRenamedFiles ;
	} // capitalizeFile()

	public void capitalizeFiles( final List< String > theDrives, final String[] extensions )
	{
		int numRenamedFiles = 0 ;
		List< File > files = new ArrayList< File >() ;

		String extensionString = "" ;
		for( String theExtension : extensions )
		{
			extensionString += theExtension + " " ;
		}

		log.info( "Searching for extensions " + extensionString + " in folders " + theDrives ) ;
		for( String theDrive : theDrives )
		{
			files.addAll( common.getFilesInDirectoryByExtension( theDrive, extensions ) ) ;
		}
		log.info( "Found " + files.size() + " file(s)" ) ;

		for( File theFile : files )
		{
			numRenamedFiles += capitalizeFile( theFile ) ;
		}
		log.info( "Renamed " + numRenamedFiles + " file(s)" ) ;
	} // capitalizeFiles()

}  // class {}
