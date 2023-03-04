package run_ffmpeg;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Provide common methods/services for this application.
 * @author Dan
 */
public class Common
{
	/// Determines the path separator
	private static boolean isWindows = true ;

	private Logger log = null ;

	/// Set testMode to true to prevent mutations
	static boolean testMode = false ;

	/// Separator to use to demarc directories
	// TODO: Make this use the System.property
	private static String pathSeparator = "\\" ;

	private static final String missingMovieMKVPath = "\\\\yoda\\MKV_Archive1\\Movies_Missing" ;
	private static final String missingTVShowMKVPath = "\\\\yoda\\MKV_Archive1\\TV Shows_Missing" ;
	private static final String missingFilePreExtension = ".missing_file" ;

	/// Paths to ffmpeg and ffprobe
	private static final String pathToFFMPEG = "D:\\Program Files\\ffmpeg\\bin\\ffmpeg" ;
	private static final String pathToFFPROBE = "D:\\Program Files\\ffmpeg\\bin\\ffprobe" ;

	/// Class-wide NumberFormat for ease of use in reporting data statistics
	private NumberFormat numFormat = null ;

	public Common( Logger log )
	{
		this.log = log ;
		numFormat = NumberFormat.getInstance( new Locale( "en", "US" ) ) ;
		numFormat.setMaximumFractionDigits( 2 ) ;
	}

	/**
	 * Add trailing path separator ("/" or "\\") if missing.
	 * @param inputPath
	 * @return
	 */
	public String addPathSeparatorIfNecessary( final String inputPath )
	{
		String retMe = inputPath ;
		if( !inputPath.endsWith( getPathSeparator() ) )
		{
			retMe = inputPath + getPathSeparator() ;
		}
		return retMe ;
	}

	public boolean executeCommand( ImmutableList.Builder< String > theCommand )
	{
		return executeCommand( toStringForCommandExecution( theCommand.build() ) ) ;
	}

	/**
	 * Execute the given command.
	 * Return true if successful, false otherwise.
	 * Returns true in all cases when in test mode.
	 * @param theCommand
	 * @return
	 */
	public boolean executeCommand( final String theCommand )
	{
		log.info( "theCommand: " + theCommand ) ;
		boolean retMe = true ;
		
		// Only execute the command if we are NOT in test mode
		if( !getTestMode() )
		{
			try
			{
				Thread.currentThread().setPriority( Thread.MIN_PRIORITY ) ;
				final Process process = Runtime.getRuntime().exec( theCommand ) ;
	
				BufferedReader errorStreamReader = new BufferedReader( new InputStreamReader( process.getErrorStream() ) ) ;
				String line = null ;
				while( (line = errorStreamReader.readLine()) != null )
				{
	
					log.info( "ErrorStream: " + line ) ;
				}
				
				if( process.exitValue() != 0 )
				{
					// Error occurred
					log.info( "Process exitValue() return error: " + process.exitValue() + ", returning false from method" ) ;
					retMe = false ;
				}
			}
			catch( Exception theException )
			{
				retMe = false ;
				log.info( "Exception: " + theException + " for command: " + theCommand ) ;
			}
		}
		return retMe ;
	}

	public boolean fileExists( final String fileNameWithPath )
	{
		final File theFile = new File( fileNameWithPath ) ;
		return theFile.exists() ;
	}

	public FFmpegProbeResult ffprobeFile( File theFile, Logger log )
	{	
		log.info( "Processing: " + theFile.getAbsolutePath() ) ;
		FFmpegProbeResult result = null ;
	
		ImmutableList.Builder<String> ffprobeExecuteCommand = new ImmutableList.Builder<String>();
		ffprobeExecuteCommand.add( getPathToFFprobe() ) ;
	
		// Add option "-v quiet" to suppress the normal ffprobe output
		ffprobeExecuteCommand.add( "-v", "quiet" ) ;
	
		// Instruct ffprobe to show streams
		ffprobeExecuteCommand.add( "-show_streams" ) ;
	
		// Instruct ffprobe to return result as json
		ffprobeExecuteCommand.add( "-print_format", "json" ) ;
	
		// Finally, add the input file
		ffprobeExecuteCommand.add( "-i", theFile.getAbsolutePath() ) ;
	
		// Build the GSON parser for the JSON input
		GsonBuilder builder = new GsonBuilder(); 
		builder.setPrettyPrinting(); 
		Gson gson = builder.create();
	
		try
		{
			Thread.currentThread().setPriority( Thread.MIN_PRIORITY ) ;
			String ffprobeExecuteCommandString = toStringForCommandExecution( ffprobeExecuteCommand.build() ) ;
			log.info( "Execute ffprobe command: " + ffprobeExecuteCommandString ) ;
	
			final Process process = Runtime.getRuntime().exec( ffprobeExecuteCommandString ) ;
	
			BufferedReader inputStreamReader = new BufferedReader( new InputStreamReader( process.getInputStream() ) ) ;
			int lineNumber = 1 ;
			String inputLine = null ;
			String inputBuffer = "" ;
			while( (inputLine = inputStreamReader.readLine()) != null )
			{
				log.fine( "" + lineNumber + "> " + inputLine ) ;
				inputBuffer += inputLine ;
				++lineNumber ;
			}
	
			if( process.exitValue() != 0 )
			{
				log.warning( "Error running ffprobe on file " + theFile.getAbsolutePath() + "; exitValue: " + process.exitValue() ) ;
				result = null ; // already null, but just for clarity
			}
			else
			{
				// Deserialize the JSON streams info from this file
				result = gson.fromJson(inputBuffer, FFmpegProbeResult.class);
				// TODO: Ensure consistent file path naming using \\yoda as start
				result.setFilename( theFile.getAbsolutePath() ) ;
				result.setProbeTime( System.currentTimeMillis() ) ;
				result.setSize( theFile.length() ) ;
				result.setLastModified( theFile.lastModified() ) ;
			}
		}
		catch( Exception theException )
		{
			theException.printStackTrace() ;
		}
		return result ;
	}

	/**
	 * Run ffprobe on the given file.
	 * If an error occurs, return null. 
	 * Otherwise, return the FFmpegProbeResult from the ffprobe.
	 */
	public FFmpegProbeResult ffprobeFile( TranscodeFile theFile, Logger log )
	{
		return ffprobeFile( new File( theFile.getMKVFileNameWithPath() ), log ) ;
	}

	/**
	 * Return a list of Files in the given directory with any of the given extensions.
	 * @param inputDirectory
	 * @param inputExtensions
	 * @return
	 */
	public List< File > getFilesInDirectoryByExtension( final String inputDirectory, final String[] inputExtensions )
	{
		List< File > filesInDirectory = new ArrayList< >() ;
		for( String extension : inputExtensions )
		{
			filesInDirectory.addAll( getFilesInDirectoryByExtension( inputDirectory, extension ) ) ;
		}
		return filesInDirectory ;
	}

	/**
	 * Return a list of files in the directoryPath with the given extension
	 * @param directoryPath
	 * @param extension
	 * @return
	 */
	public List< File > getFilesInDirectoryByExtension( final String directoryPath, final String extension )
	{
		List< File > filesInDirectoryWithExtension = new ArrayList< >() ;
		try
		{
			Stream< Path > walk = Files.walk( Paths.get( directoryPath ) ) ;
			List< String > fileNames = walk.filter( Files::isRegularFile ).map( x -> x.toString() ).collect( Collectors.toList() ) ;
			walk.close() ;
	
			// Filter by extension
			for( String fileName : fileNames )
			{
				if( fileName.endsWith( extension ) )
				{
					filesInDirectoryWithExtension.add( new File( fileName ) ) ;
				}
			}
		} // try()
		catch( Exception theException )
		{
			log.info( " (" + directoryPath + ")> Exception: " + theException ) ;
		}
		return filesInDirectoryWithExtension ;
	}

	/**
	 * Return the path separator based on this file system.
	 * @return
	 */
	public String getPathSeparator()
	{
		String retMe = pathSeparator ;
		if( !getIsWindows() )
		{
			// Linux naming
			retMe = "\\\\" ;
		}
		return retMe ;
	}

	public List< File > getSubDirectories( final File directoryPathFile )
	{
		File[] directories = directoryPathFile.listFiles( File::isDirectory ) ;
		return Arrays.asList( directories ) ;
	}

	/**
	 * Retrieve all subdirectories to the given directoryPath.
	 * Note that this excludes the parent directory.
	 * Example: directoryPath = F:/Movies, return all directories underneath it (like "Elf" and "Mr. Deeds"), but not the
	 * directory itself (F:/Movies)
	 * @param directoryPath
	 * @return
	 */
	public List< File > getSubDirectories( final String directoryPath )
	{
		return getSubDirectories( new File( directoryPath ) ) ;
	}

	public boolean hasInputFileInDirectory( final File theDirectory, final String extension )
	{
		return hasInputFileInDirectory( theDirectory.getAbsolutePath(), extension ) ;
	}

	/**
	 * Return true if a file with any of the given extensions exists in the given directory.
	 * @param theDirectory
	 * @param extensionsToCheck
	 * @return
	 */
	public boolean hasInputFileInDirectory( final File theDirectory, final String[] extensionsToCheck )
	{
		for( String extension : extensionsToCheck )
		{
			if( hasInputFileInDirectory( theDirectory, extension ) )
			{
				return true ;
			}
		}
		return false ;
	}

	public boolean hasInputFileInDirectory( final String directoryName, final String extension )
	{
		List< File > inputFileNameList = getFilesInDirectoryByExtension( directoryName, extension ) ;
		return (inputFileNameList.size() > 0) ;
	}

	public void makeDirectory( final String directoryName )
	{
		try
		{
			File directoryFile = new File( directoryName ) ;
			if( !directoryFile.exists() )
			{
				log.info( "Making directory structure: " + directoryName ) ;
				if( !testMode && !directoryFile.mkdirs() )
				{
					log.info( "Unable to mkdirs (" + directoryName + ")" ) ;
				}
			}
	
		}
		catch( Exception theException )
		{
			log.info( "Exception: (\"" + directoryName + "\"): " + theException.toString() ) ;
		}
	}
	
	public String makeElapsedTimeString( final long startTimeMillis, final long stopTimeMillis )
	{
		final double timeElapsedInSeconds = (stopTimeMillis - startTimeMillis) / 1000000000.0 ;
		String retMe = "Total elapsed time: "
				+ getNumberFormat().format( timeElapsedInSeconds )
				+ " seconds, "
				+ getNumberFormat().format( timeElapsedInSeconds / 60.0 )
				+ " minutes" ;
		return retMe ; 
	}

	/**
	 * Setup a logger stream for the given filename and class.
	 * @param logFileName
	 * @param className
	 * @return
	 */
	public static Logger setupLogger( final String logFileName, final String className )
	{
		Logger log = Logger.getLogger( className ) ;
		try
		{
			// Disable default handlers
			log.setUseParentHandlers( false ) ;
			FileHandler logFileHandler = new FileHandler( logFileName ) ;
			logFileHandler.setFormatter( new MyLogFormatter() );
			log.addHandler( logFileHandler ) ;

			ConsoleHandler ch = new ConsoleHandler() ;
			ch.setFormatter( new MyLogFormatter() ) ;
			log.addHandler( ch ) ;
		}
		catch( Exception theException )
		{
			System.out.println( "BuildMovieAndShowIndex> Unable to create logger FileHandler as file "
					+ logFileName
					+ ": " + theException ) ;
		}
		log.setLevel( Level.ALL ) ;
		System.out.println( "setupLogger> Established logger with log filename: " + logFileName ) ;
		return log ;
	}

	public synchronized boolean shouldStopExecution( final String fileName )
	{
		final File stopFile = new File( fileName ) ;
		boolean fileExists = stopFile.exists() ;
		return fileExists ;
	}

	public String toStringForCommandExecution( final ImmutableList< String > theList )
	{
		String retMe = "" ;
		for( Iterator< String > listIterator = theList.iterator() ; listIterator.hasNext() ; )
//		for( String listItem : theList )
		{
			// Any file names with spaces must be encapsulated in double quotes, except for those
			// items that already start with "
			String arg = listIterator.next();
			if( arg.contains( " " ) && !arg.startsWith( "\"" ) && !arg.endsWith( "\"" ) )
			{
				retMe += "\"" ;
			}
			retMe += arg ;

			// Any file names with spaces must be encapsulated in double quotes, except for those
			// items that already start with "
			if( arg.contains( " " ) && !arg.startsWith( "\"" ) && !arg.endsWith( "\"" ) )
			{
				retMe += "\"" ;
			}
			
			if( listIterator.hasNext() )
			{
				// At least one more item remaining, add a space
				retMe += " " ;
			}
		}
		return retMe ;
	}
	
	public void touchFile( final String fileName )
	{
		try
		{
			File theTouchFile = new File( fileName ) ;
			if( !theTouchFile.exists() )
			{
				theTouchFile.createNewFile() ;
			}
		}
		catch( Exception e )
		{
			log.info( "TranscodeFile.touchFile> Exception for file " + fileName + ": " + e ) ;
		}
	}
	
	public boolean getIsWindows()
	{
		return isWindows ;
	}

	public String getMissingMovieMKVPath() {
		return missingMovieMKVPath;
	}

	public String getMissingTVShowMKVPath() {
		return missingTVShowMKVPath;
	}

	public NumberFormat getNumberFormat()
	{
		return numFormat ;
	}

	public static String getPathToFFmpeg() {
		return pathToFFMPEG;
	}

	public static String getPathToFFprobe() {
		return pathToFFPROBE;
	}

	public boolean getTestMode()
	{
		return testMode ;
	}

	public void setTestMode( boolean newValue )
	{
		testMode = newValue ;
	}

	public String toString( final String[] inputArray )
	{
		String retMe = "{" ;
		for( String theInput : inputArray )
		{
			retMe += "[" + theInput + "]" ;
		}
		retMe += "}" ;
		return retMe ;
	}

	/**
	 * Return the string that will be included in each fake file to indicate that it is 
	 *  a missing file (.mkv).
	 * Includes the preceding '.'
	 * @return
	 */
	public String getMissingFilePreExtension() {
		return missingFilePreExtension;
	}

}
