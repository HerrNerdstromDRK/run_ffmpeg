package run_ffmpeg;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
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

	/// The log stream to use internally and share with other objects in this process.
	private static Logger log = null ;

	/// Set testMode to true to prevent mutations
	private static boolean testMode = false ;

	// Set to true to move the mkv files, false otherwise
	// Only used for certain applications
	private static boolean doMoveFiles = true ;

	/// Separator to use to demarc directories
	// TODO: Make this use the System.property
	private static String pathSeparator = "\\" ;

	/// The name of the primary file server
	private static final String primaryFileServerName = "\\\\skywalker" ;

	private static final String[] pathsToFFMPEG =
		{
				"c:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe",
				"d:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe"
		} ;

	private static final String[] pathsToFFPROBE =
		{
				"c:\\Program Files\\ffmpeg\\bin\\ffprobe.exe",
				"d:\\Program Files\\ffmpeg\\bin\\ffprobe.exe"
		} ;

	private static final String[] pathsToSubtitleEdit =
		{
				"c:\\Program Files\\Subtitle Edit\\SubtitleEdit.exe",
				"d:\\Program Files\\Subtitle Edit\\SubtitleEdit.exe"
		} ;

	private static final String[] pathsToDefaultMP4OutputDirectory =
		{
				"d:\\temp",
				"d:\\tmp",
				"c:\\temp",
				"c:\\tmp"
		} ;

	/// Paths to external applications
	private String pathToFFMPEG = null ;
	private String pathToFFPROBE = null ;
	private String pathToSubtitleEdit = null ;
	private String pathToDefaultMP4OutputDirectory = null ;
	private String pathToTmpDir = null ;

	/// The replacement file name for correlated files that are missing. This is for
	/// user interface reporting via the web interface.
	private static final String missingFileSubstituteName = "(none)" ;

	/// The size of an SRT file, in bytes, that represents the minimum valid file length.
	private static final int minimumSRTFileSize = 25 ;

	private static final String analyzeDurationString = "5G" ;
	private static final String probeSizeString = "5G" ;

	private static final String moviesFolderName = "Movies" ;
	private static final String tvShowsFolderName = "TV_Shows" ;
	private static final String pathToMovies = primaryFileServerName + "\\Media\\" + moviesFolderName ;
	private static final String pathToTVShows = primaryFileServerName + "\\Media\\" + tvShowsFolderName ;
	private static final String otherVideosFolderName = "Other_Videos" ;
	private static final String pathToMP4s = primaryFileServerName + "\\Media" ;
	private static final String pathToMKVs = primaryFileServerName + "\\Media" ;
	private static final String pathToMP4TVShows = pathToMP4s + "\\" + tvShowsFolderName ;
	private static final String pathToMP4Movies = pathToMP4s + "\\" + moviesFolderName ;
	private static final String pathToMKVTVShows = pathToMKVs + "\\" + tvShowsFolderName ;
	private static final String pathToMKVMovies = pathToMKVs + "\\" + moviesFolderName ;
	private static final String pathToOCRInputDirectory = pathToMKVs + "\\To OCR" ;

	/// The string to search for in file paths to determine if this is a tv show
	private static final String tvPathCheckString = "TV_Shows" ;

	/// The directories to probe
	/// Broken down into the two USB chains so that applications can
	/// multithread access to the MP4/MKV drives
	private final String[] allChainAMP4Drives =
		{
				"\\\\skywalker\\Media"
		} ;
	private final String[] allChainBMP4Drives =
		{} ;
	private final String[] allChainAMKVDrives =
		{
				"\\\\\\skywalker\\Media"
		} ;
	private final String[] allChainBMKVDrives =
		{} ;

	/// Class-wide NumberFormat for ease of use in reporting data statistics
	private NumberFormat numFormat = null ;

	public Common()
	{
		this( setupLogger( "log_common.txt", "Common" ) ) ;
	}

	public Common( Logger log )
	{
		if( null == Common.log ) Common.log = log ;
		numFormat = NumberFormat.getInstance( new Locale.Builder().setLanguage( "en" ).setRegion( "US" ).build() ) ;
		numFormat.setMaximumFractionDigits( 2 ) ;
		setupPaths() ;
	}

	protected void setupPaths()
	{
		for( String testLocation : pathsToFFMPEG )
		{
			if( (new File( testLocation )).isFile() )
			{
				// Found the file
				setPathToFFmpeg( testLocation ) ;
				break ;
			}
		}
		if( null == getPathToFFmpeg() )
		{
			log.warning( "Unable to find ffmpeg" ) ;
		}

		for( String testLocation : pathsToFFPROBE )
		{
			if( (new File( testLocation )).isFile() )
			{
				// Found the file
				setPathToFFprobe( testLocation ) ;
				break ;
			}
		}
		if( null == getPathToFFprobe() )
		{
			log.warning( "Unable to find ffprobe" ) ;
		}

		for( String testLocation : pathsToSubtitleEdit )
		{
			if( (new File( testLocation )).isFile() )
			{
				// Found the file
				setPathToSubtitleEdit( testLocation ) ;
				break ;
			}
		}
		if( null == getPathToSubtitleEdit() )
		{
			log.warning( "Unable to find SubtitleEdit" ) ;
		}

		for( String testLocation : pathsToDefaultMP4OutputDirectory )
		{
			if( (new File( testLocation )).isDirectory() )
			{
				// Found the file
				setPathToDefaultMP4OutputDirectory( testLocation ) ;
				break ;
			}
		}
		if( null == getPathToDefaultMP4OutputDirectory() )
		{
			log.warning( "Unable to find defaultMP4OutputDirectory" ) ;
		}

		if( (new File( "d:\\temp" )).isDirectory() )
		{
			pathToTmpDir = "d:\\temp" ;
		}
		else if( (new File( "c:\\temp" )).isDirectory() )
		{
			pathToTmpDir = "c:\\temp" ;
		}
		else
		{
			log.warning( "Unable to find tmp dir" ) ;
		}
	}

	/**
	 * Add the Movies and TV Shows subfolder names to each directory path given.
	 * @param theDrives
	 * @return
	 */
	public List< String > addMoviesAndTVShowFoldersToEachDrive( final List< String > theDrives )
	{
		List< String > retMe = new ArrayList< String >() ;
		for( String theDrive : theDrives )
		{
			final String moviesFolder = addPathSeparatorIfNecessary( theDrive ) + "Movies" ;
			final String tvShowsFolder = addPathSeparatorIfNecessary( theDrive ) + "TV_Shows" ;
			//			final String otherVideosFolder = addPathSeparatorIfNecessary( theDrive ) + "Other Videos" ;

			retMe.add( tvShowsFolder ) ;
			retMe.add( moviesFolder ) ;
			//			retMe.add( otherVideosFolder ) ;
		}
		return retMe ;
	}

	public List< String > addMoviesAndTVShowFoldersToDrive( final String theDrive )
	{
		List< String > tempList = new ArrayList< String >() ;
		tempList.add( theDrive ) ;
		return addMoviesAndTVShowFoldersToEachDrive( tempList ) ;
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

	public List< String > addToConvertToEachDrive( final List< String > theDrives )
	{
		List< String > retMe = new ArrayList< String >() ;
		for( String theDrive : theDrives )
		{
			final String moviesFolder = addPathSeparatorIfNecessary( theDrive ) + getPathSeparator() + "To Convert" ;
			final String tvShowsFolder = addPathSeparatorIfNecessary( theDrive ) + getPathSeparator() + "To Convert - TV Shows" ;

			retMe.add( moviesFolder ) ;
			retMe.add( tvShowsFolder ) ;
		}
		return retMe ;
	}

	public String arrayToString( final String[] stringArray )
	{
		String retMe = "" ;
		for( String theString : stringArray )
		{
			retMe += theString + " " ;
		}
		return retMe ;
	}

	/**
	 * Execute the given command.
	 * Return true if successful, false otherwise.
	 * Returns true in all cases when in test mode.
	 * @param theCommand
	 * @return
	 */
	public boolean executeCommand( ImmutableList.Builder< String > theCommand )
	{
		log.info( "theCommand: " + Arrays.toString( theCommand.build().toArray( new String[ 1 ] ) ) ) ;
		boolean retMe = true ;

		// Only execute the command if we are NOT in test mode
		if( !getTestMode() )
		{
			try
			{
				Thread.currentThread().setPriority( Thread.MIN_PRIORITY ) ;
				final ProcessBuilder theProcessBuilder = new ProcessBuilder( theCommand.build().toArray( new String[ 1 ] ) ) ;
				final Process process = theProcessBuilder.start() ;

				BufferedReader inputStreamReader = process.inputReader() ;
				BufferedReader errorStreamReader =  process.errorReader() ;

				while( process.isAlive() )
				{
					String inputStreamLine = null ;
					String lastInputStreamLine = "" ; // never null
					String errorStreamLine = null ;
					String lastErrorStreamLine = "" ; // never null

					// Read the error stream first
					while( errorStreamReader.ready() )
					{
						errorStreamLine = errorStreamReader.readLine() ;
						if( errorStreamLine != null )
						{
							if( errorStreamLine.equalsIgnoreCase( lastErrorStreamLine ) )
							{
								// Same as last error input
								continue ;
							}
							lastErrorStreamLine = errorStreamLine ;
							if( !filterOut( errorStreamLine ) )
							{
								log.info( "ErrorStream: " + errorStreamLine ) ;
							}
						}
					}
					while( inputStreamReader.ready() )
					{
						inputStreamLine = inputStreamReader.readLine() ;
						if( inputStreamLine != null )
						{
							if( inputStreamLine.equalsIgnoreCase( lastInputStreamLine ) )
							{
								// Same as last input
								continue ;
							}
							lastInputStreamLine = inputStreamLine ;
							if( !filterOut( inputStreamLine ) )
							{
								log.info( "InputStream: " + inputStreamLine ) ;
							}
						}
					}
					if( (null == inputStreamLine) && (null == errorStreamLine) )
					{
						// Neither stream had data.
						// Pause to wait for input.
						Thread.sleep( 100 ) ;
					}
				} // while( process.isAlive() )
				// Post-condition: Process has terminated.

				// Check if the process has exited.
				if( process.exitValue() != 0 )
				{
					// Error occurred
					retMe = false ;
					log.warning( "Process exitValue() return error: " + process.exitValue() + ", returning false from method; info: " + process.info().toString() ) ;
				}
			}
			catch( Exception theException )
			{
				retMe = false ;
				log.warning( "Exception: " + theException + " for command: " + theCommand ) ;
			}
		}
		return retMe ;
	}

	/**
	 * Return true if the given file exists, false otherwise.
	 * @param fileNameWithPath
	 * @return
	 */
	public boolean fileExists( final String fileNameWithPath )
	{
		final File theFile = new File( fileNameWithPath ) ;
		return theFile.exists() ;
	}

	/**
	 * Return true if the given input line of text should be filtered from showing to the user.
	 * Return false otherwise.
	 * @param inputLine
	 * @return
	 */
	public boolean filterOut( final String inputLine )
	{
		if( inputLine.isEmpty() || inputLine.isBlank() )
		{
			return true ;
		}
		if( inputLine.contains( "Empty page!!" ) )
		{
			// Useless error line from PgsToSRT
			return true ;
		}
		return false ;
	}

	/**
	 * Finds all files in a given directory path that match the pattern.
	 * @param directoryPath
	 * @param pattern
	 * @return
	 * @throws IOException
	 */
	public List< Path > findFiles( final String directoryPath, final String pattern )
	{
		assert( directoryPath != null ) ;
		assert( pattern != null ) ;

		List< Path > matchedFiles = new ArrayList<>() ;
		Path startingDir = Paths.get( directoryPath ) ;
		Pattern regexPattern = Pattern.compile( pattern ) ;

		FileVisitor< Path > visitor = new SimpleFileVisitor< Path >()
		{
			@Override
			public FileVisitResult visitFile( Path file, BasicFileAttributes attrs )
			{
				if( regexPattern.matcher( file.getFileName().toString() ).matches() )
				{
					matchedFiles.add( file ) ;
				}
				return FileVisitResult.CONTINUE ;
			}

			@Override
			public FileVisitResult visitFileFailed( Path file, IOException exc )
			{
				log.warning( "Failed to access file: " + file + ". Reason: " + exc.getMessage() ) ;
				return FileVisitResult.CONTINUE ;
			}
		};

		try
		{
			Files.walkFileTree( startingDir, visitor ) ;
		}
		catch( Exception theException )
		{
			log.warning( "Exceptoin while finding pattern \"" + pattern + "\" in directory " + directoryPath + ": " + theException.toString() ) ;
		}
		return matchedFiles ;
	}

	/**
	 * Return all directories at the lowest level available inside of the given directoryPath.
	 * Returns only the lowest level directories, and nothing in between.
	 */
	public List< String > findLowestLevelDirectories( final String topLevelDirectory )
	{
		//		log.info( "Checking tld: " + topLevelDirectory ) ;
		List< String > allDirectories = new ArrayList< String >() ;

		Set< String > dirNames = Stream.of(new File( topLevelDirectory ).listFiles())
				.filter(file -> file.isDirectory())
				.map(File::getName)
				.collect(Collectors.toSet());
		if( dirNames.isEmpty() )
		{
			// Base case -- we are at the lowest level directory.
			// Add this directory to the list of directories to return.
			allDirectories.add( topLevelDirectory ) ;
		}
		else
		{
			for( String dirName : dirNames )
			{
				//				log.info( "Calling findLowestLevelDirectories ( " + topLevelDirectory + "\\" + dirName + " )" ) ;
				allDirectories.addAll( findLowestLevelDirectories( topLevelDirectory + "\\" + dirName ) ) ;
			}
		}
		return allDirectories ;
	}

	/**
	 * Probe the given file and report out to the given log stream.
	 * @param theFile
	 * @param log
	 * @return
	 */
	public FFmpegProbeResult ffprobeFile( File theFile, Logger log )
	{	
		log.fine( "Processing: " + theFile.getAbsolutePath() ) ;
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
			log.info( Arrays.toString( ffprobeExecuteCommand.build().toArray( new String[ 1 ] ) ) ) ;

			final ProcessBuilder theProcessBuilder = new ProcessBuilder( ffprobeExecuteCommand.build().toArray( new String[ 1 ] ) ) ;
			final Process process = theProcessBuilder.start() ;

			BufferedReader inputStreamReader = new BufferedReader( new InputStreamReader( process.getInputStream() ) ) ;
			//			int lineNumber = 1 ;
			String inputLine = null ;
			String inputBuffer = "" ;
			while( (inputLine = inputStreamReader.readLine()) != null )
			{
				//				log.fine( "" + lineNumber + "> " + inputLine ) ;
				inputBuffer += inputLine ;
				//				++lineNumber ;
			}

			if( process.exitValue() != 0 )
			{
				log.warning( "Error running ffprobe on file " + theFile.getAbsolutePath() + "; exitValue: " + process.exitValue() ) ;
				result = null ; // already null, but just for clarity
			}
			else
			{
				// Deserialize the JSON streams info from this file
				result = gson.fromJson( inputBuffer, FFmpegProbeResult.class ) ;

				result.setFileNameWithPath( theFile.getAbsolutePath() ) ;
				result.setFileNameWithoutPath( theFile.getName() ) ;
				result.setFileNameShort( shortenFileName( theFile.getAbsolutePath() ) ) ;
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
		return ffprobeFile( new File( theFile.getMKVInputFileNameWithPath() ), log ) ;
	}

	/**
	 * Given the list of folders, some of whom may be overlapping on the same drive, separate the folders
	 *  into one list per drive.
	 * For example, if foldersToExtract looks like this:
	 *  ["\\\\yoda\\MKV_Archive1\\To Convert", "\\\\yoda\\MKV_Archive1\\To Convert - TV Shows","E:\","C:\temp"]
	 * then return this list:
	 * 	[["\\\\yoda\\MKV_Archive1\\To Convert", "\\\\yoda\\MKV_Archive1\\To Convert - TV Shows"],["E:\"],["C:\temp"]]
	 * @param foldersToExtract
	 * @return
	 */
	public static Map< String, List< String > > getDrivesAndFolders( final List< String > foldersToExtract )
	{
		assert( foldersToExtract != null ) ;

		// This Map is indexed by the drive, with trailing separators.
		// The value for each index is the list of folders on that drive to extract.
		Map< String, List< String > > mappedFolders = new HashMap< String, List< String > >() ;
		for( String folderToExtract : foldersToExtract )
		{
			final String theDrive = getDriveWithPathSeparator( folderToExtract ) ;
			List< String > foldersForThisDrive = mappedFolders.get( theDrive ) ;
			if( null == foldersForThisDrive )
			{
				foldersForThisDrive = new ArrayList< String >() ;
				mappedFolders.put( theDrive, foldersForThisDrive ) ;
			}
			foldersForThisDrive.add( folderToExtract ) ;
		}
		return mappedFolders ;
	}

	/**
	 * Return the drive on which the given theFolder resides.
	 * Examples:
	 *   - "C:\temp\thefile.mkv" -> "C:\\"
	 *   - "\\\\yoda\\MKV_Archive1\\To Convert\\Transformers (2006)" -> "\\\\yoda\\MKV_Archive1\\"
	 * @param theFolder
	 * @return
	 */
	public static String getDriveWithPathSeparator( final String theFolder )
	{
		assert( theFolder != null ) ;
		assert( !theFolder.isEmpty() ) ;
		assert( !theFolder.isBlank() ) ;

		Path thePath = Paths.get( theFolder ) ;
		final String fileRoot = thePath.getRoot().toString() ;

		return fileRoot ;		
	}

	/**
	 * Return a list of Files in the given directory with any of the given extensions.
	 * @param inputDirectory
	 * @param inputExtensions
	 * @return non-null, but perhaps empty
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
	 * @return non-null, but perhaps empty
	 */
	public List< File > getFilesInDirectoryByExtension( final String directoryPath, final String extension )
	{
		List< File > filesInDirectoryWithExtension = new ArrayList< >() ;
		File directoryPathFile = new File( directoryPath ) ;
		if( !directoryPathFile.exists() )
		{
			return filesInDirectoryWithExtension ;
		}

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

	public static int getMinimumSRTFileSize()
	{
		return minimumSRTFileSize;
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

	/**
	 * Return the season string for a given file.
	 * For example, for the inputFile of "\\\\yoda\\MP4\\TV Shows\\Scrubs\\Season 06\\Scrubs - S06E01 - episode name.mkv"
	 *  return "Season 06"
	 * If this is a movie file, and has no season, just return the empty String.
	 * @param inputFile Is a file, not a path.
	 * @return
	 */
	public static String getSeasonString( final File inputFile )
	{
		assert( inputFile != null ) ;
		assert( inputFile.getAbsolutePath().contains( "." ) ) ;

		if( !inputFile.getAbsolutePath().contains( "Season " ) )
		{
			return "" ;
		}
		// Post condition: TV Show with "Season XX" name

		// Get to the segment with the season string
		String seasonString = inputFile.getParentFile().getName() ;
		return seasonString ;
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
			Path directoryPath = Paths.get( directoryName ) ;
			log.info( "Making directory structure: " + directoryPath.toString() ) ;
			Files.createDirectories( directoryPath ) ;
		}
		catch( Exception theException )
		{
			log.info( "Exception creating directory (\"" + directoryName + "\"): " + theException.toString() ) ;
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
	 * Returns a new String without the last extension.
	 * @param fileName
	 * @return
	 */
	public static String removeFileNameExtension( final String fileName )
	{
		String fileNameWithoutExtension = fileName.substring( 0, fileName.lastIndexOf( '.' ) ) ;
		return fileNameWithoutExtension ;
	}

	/**
	 * Remove the trailing backslash at the end of a string, if it exists, and return
	 *  the new string.
	 * @param input
	 * @return
	 */
	public static String removeTrailingBackslash( final String input )
	{
		String retMe = new String( input ) ;
		if( input.endsWith( "\\" ) )
		{
			retMe = input.substring( 0, input.length() - 1 ) ;
		}
		return retMe ;
	}

	public static String replaceExtension( final String fileName, final String newExtension )
	{
		final String fileNameWithoutExtension = removeFileNameExtension( fileName ) ;
		String fileNameWithNewExtension = fileNameWithoutExtension ;
		if( !newExtension.startsWith( "." ) )
		{
			fileNameWithNewExtension += "." ;
		}
		fileNameWithNewExtension += newExtension ;
		return fileNameWithNewExtension ;
	}

	/**
	 * Setup a logger stream for the given filename and class.
	 * @param logFileName
	 * @param className
	 * @return
	 */
	public static Logger setupLogger( final String logFileName, final String className )
	{
		return setupLogger( logFileName, className, false ) ;
	}

	/**
	 * Setup a logger stream for the given filename and class.
	 * @param logFileName
	 * @param className
	 * @param forceNewLogger: set to true if a new logger is to be built. It will continue to store the old logger.
	 * @return
	 */
	public static Logger setupLogger( final String logFileName, final String className, boolean forceNewLogger )
	{
		// Keep only a single log instance per process
		boolean logIsNull = (null == log) ? true : false ;

		// Use localLog as the primary reference in this method
		// Need to account for three scenarios:
		// log is null: Create a new localLog, store it as the class log, and return it
		// log is non-null and forceNewLogger is false: return log (as localLog)
		// log is non-null and forceNewLogger is true: create a new localLog and return it (do not change log)
		Logger localLog = log ;
		if( (null == log) || forceNewLogger )
		{
			// First time creating a log stream.
			// Retrieve the log instance and setup the parameters for this process.
			localLog = Logger.getLogger( className ) ;

			try
			{
				// Disable default handlers
				localLog.setUseParentHandlers( false ) ;
				FileHandler logFileHandler = new FileHandler( logFileName ) ;
				logFileHandler.setFormatter( new MyLogFormatter() );
				localLog.addHandler( logFileHandler ) ;

				ConsoleHandler ch = new ConsoleHandler() ;
				ch.setFormatter( new MyLogFormatter() ) ;
				localLog.addHandler( ch ) ;
			}
			catch( Exception theException )
			{
				System.out.println( "BuildMovieAndShowIndex> Unable to create logger FileHandler as file "
						+ logFileName
						+ ": " + theException ) ;
			}
			localLog.setLevel( Level.ALL ) ;

			// Was the log initially null?
			if( logIsNull )
			{
				// First use -- keep this logger as permanent
				log = localLog ;
			}
		}

		//		log.fine( "Established logger: " + localLog.getName() ) ;
		return localLog ;
	}

	/**
	 * Strip most of the information about a file's absolute path.
	 * This is targeted to how I currently have the workflow setup, wherein
	 *  each file used by this suite of tools includes the full path name:
	 *   \\yoda\\MKV_Archive1\\Movies\\Transformers (2002)\\Transformers (2002).mkv
	 * That string is too long to present in a web interface side by side with other things,
	 *  so this method will shorten to something like "MKV_1\\Transformers (2002).mkv"
	 * @param inputName
	 * @return
	 */
	public static String shortenFileName( final String inputName )
	{
		String retMe = "" ;
		StringTokenizer tokens = new StringTokenizer( inputName, "\\" ) ;

		// Walk through the tokens to build the shortened file name
		// Keep only "MKV_#" and the actual file name.
		while( tokens.hasMoreTokens() )
		{
			String nextToken = tokens.nextToken() ;
			if( nextToken.contains( "MKV_" ) || nextToken.contains( "MP4" ) )
			{
				retMe += nextToken.replace( "Archive", "" ) + "\\" ;
			}
			else if( nextToken.contains( ".mkv" ) || nextToken.contains( ".mp4" ) )
			{
				retMe += nextToken ;
			}
		}
		return retMe ;
	}

	/**
	 * Return true if the stop file exists, otherwise return false.
	 * @param fileName
	 * @return
	 */
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
			log.info( "Exception for file " + fileName + ": " + e ) ;
		}
	}

	public List< String > getAllChainAMKVDrives()
	{
		List< String > retMe = new ArrayList< String >( Arrays.asList( allChainAMKVDrives) ) ;
		return retMe ;
	}

	public List< String > getAllChainAMKVDrivesAndFolders()
	{
		return addMoviesAndTVShowFoldersToEachDrive( getAllChainAMKVDrives() ) ;
	}

	public List< String > getAllChainBMKVDrives()
	{
		List< String > retMe = new ArrayList< String >( Arrays.asList( allChainBMKVDrives ) ) ;
		return retMe ;
	}

	public List< String > getAllChainBMKVDrivesAndFolders()
	{
		return addMoviesAndTVShowFoldersToEachDrive( getAllChainBMKVDrives() ) ;
	}

	public List< String > getAllChainAMP4Drives()
	{
		List< String > retMe = new ArrayList< String >( Arrays.asList( allChainAMP4Drives ) ) ;
		return retMe ;
	}

	public List< String > getAllChainAMP4DrivesAndFolders()
	{
		return addMoviesAndTVShowFoldersToEachDrive( getAllChainAMP4Drives() ) ;
	}

	public List< String > getAllChainBMP4Drives()
	{
		List< String > retMe = new ArrayList< String >( Arrays.asList( allChainBMP4Drives) ) ;
		return retMe ;
	}

	public List< String > getAllChainBMP4DrivesAndFolders()
	{
		return addMoviesAndTVShowFoldersToEachDrive( getAllChainBMP4Drives() ) ;
	}

	public List< String > getAllDrivesAndFolders()
	{
		List< String > retMe = new ArrayList< String >() ;
		retMe.addAll( getAllMP4DrivesAndFolders() ) ;
		retMe.addAll( getAllMKVDrivesAndFolders() ) ;

		return retMe ;
	}

	public List< String > getAllDrives()
	{
		List< String > retMe = new ArrayList< String >() ;
		retMe.addAll( getAllMKVDrives() ) ;
		retMe.addAll( getAllMP4Drives() ) ;
		return retMe ;
	}

	public List< String > getAllMKVDrives()
	{
		List< String > retMe = new ArrayList< String >() ;
		retMe.addAll( getAllChainAMKVDrives() )  ;
		retMe.addAll( getAllChainBMKVDrives() )  ;
		return retMe ;
	}

	public List< String > getAllMKVDrivesAndFolders()
	{
		List< String > retMe = new ArrayList< String >() ;
		retMe.addAll( getAllChainAMKVDrivesAndFolders() ) ;
		retMe.addAll( getAllChainBMKVDrivesAndFolders() ) ;
		return retMe ;
	}

	public List< String > getAllMP4Drives()
	{
		List< String > retMe = new ArrayList< String >() ;
		retMe.addAll( getAllChainAMP4Drives() )  ;
		retMe.addAll( getAllChainBMP4Drives() )  ;
		return retMe ;
	}

	public List< String > getAllMP4DrivesAndFolders()
	{
		List< String > retMe = new ArrayList< String >() ;
		retMe.addAll( getAllChainAMP4DrivesAndFolders() ) ;
		retMe.addAll( getAllChainBMP4DrivesAndFolders() ) ;
		return retMe ;
	}

	public static String getAnalyzeDurationString()
	{
		return analyzeDurationString;
	}

	public boolean getIsWindows()
	{
		return isWindows ;
	}

	public static String getMissingFileSubstituteName()
	{
		return missingFileSubstituteName;
	}
	//
	//	public String getMKVDriveWithMostAvailableSpace()
	//	{
	//		String mkvDriveWithMostAvailableSpace = "" ;
	//		double largestFreeSpaceSoFar = 0.0 ;
	//
	//		final List< String > allMKVDrives = getAllMKVDrives() ;
	//		for( String mkvDrive : allMKVDrives )
	//		{
	//			final File mkvDriveFile = new File( mkvDrive ) ;
	//			final double freeSpaceForThisDrive = mkvDriveFile.getFreeSpace() ;
	//
	//			if( (null == mkvDriveWithMostAvailableSpace)
	//					|| (freeSpaceForThisDrive > largestFreeSpaceSoFar) )
	//			{
	//				// Found a new largest drive.
	//				mkvDriveWithMostAvailableSpace = mkvDrive ;
	//				largestFreeSpaceSoFar = freeSpaceForThisDrive ;
	//			}
	//		}
	//		return mkvDriveWithMostAvailableSpace ;
	//	}
	//
	//	public String getMP4DriveWithMostAvailableSpace()
	//	{
	//		String mp4DriveWithMostAvailableSpace = "" ;
	//		double largestFreeSpaceSoFar = 0.0 ;
	//
	//		final List< String > allMP4Drives = getAllMP4Drives() ;
	//		for( String mp4Drive : allMP4Drives )
	//		{
	//			final File mp4DriveFile = new File( mp4Drive ) ;
	//			final double freeSpaceForThisDrive = mp4DriveFile.getFreeSpace() ;
	//
	//			if( (null == mp4DriveWithMostAvailableSpace)
	//					|| (freeSpaceForThisDrive > largestFreeSpaceSoFar) )
	//			{
	//				// Found a new largest drive.
	//				mp4DriveWithMostAvailableSpace = mp4Drive ;
	//				largestFreeSpaceSoFar = freeSpaceForThisDrive ;
	//			}
	//		}
	//		return mp4DriveWithMostAvailableSpace ;
	//	}

	public NumberFormat getNumberFormat()
	{
		return numFormat ;
	}

	public String getPathToDefaultMP4OutputDirectory()
	{
		return pathToDefaultMP4OutputDirectory ;
	}

	public String getPathToFFmpeg()
	{
		return pathToFFMPEG;
	}

	public String getPathToFFprobe()
	{
		return pathToFFPROBE;
	}

	protected String getPathToSubtitleEdit()
	{
		return pathToSubtitleEdit;
	}

	public String getPathToTmpDir()
	{
		return pathToTmpDir ;
	}

	public static String getPrimaryfileservername()
	{
		return primaryFileServerName;
	}

	public static String getProbeSizeString()
	{
		return probeSizeString;
	}

	public boolean getTestMode()
	{
		return testMode ;
	}

	public boolean isDoMoveFiles()
	{
		return doMoveFiles;
	}

	public void setPathToDefaultMP4OutputDirectory( final String pathToDefaultMP4OutputDirectory )
	{
		this.pathToDefaultMP4OutputDirectory = pathToDefaultMP4OutputDirectory ;
	}

	public void setDoMoveFiles( boolean doMoveFiles )
	{
		Common.doMoveFiles = doMoveFiles;
	}

	public void setPathToFFmpeg( final String pathToFFMPEG )
	{
		this.pathToFFMPEG = pathToFFMPEG ;
	}

	public void setPathToFFprobe( final String pathToFFPROBE )
	{
		this.pathToFFPROBE = pathToFFPROBE ;
	}

	public void setPathToSubtitleEdit( final String pathToSubtitleEdit )
	{
		this.pathToSubtitleEdit = pathToSubtitleEdit ;
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

	public static String getPathToMP4s ()
	{
		return pathToMP4s ;
	}

	public static String getPathToMKVs()
	{
		return pathToMKVs ;
	}

	public static String getPathToMP4TVShows()
	{
		return pathToMP4TVShows ;
	}

	public static String getPathToMP4Movies()
	{
		return pathToMP4Movies ;
	}

	public static String getPathToMKVTVShows()
	{
		return pathToMKVTVShows ;
	}

	public static String getPathToMKVMovies()
	{
		return pathToMKVMovies ;
	}

	public static String getPathToOCRInputDirectory()
	{
		return pathToOCRInputDirectory ;
	}

	public static String getTVPathCheckString()
	{
		return tvPathCheckString ;
	}

	public static String getMoviesFolderName()
	{
		return moviesFolderName;
	}

	public static String getTVShowsFolderName()
	{
		return tvShowsFolderName;
	}

	public static String getOtherVideosFolderName()
	{
		return otherVideosFolderName;
	}

	public static String getPathToMovies()
	{
		return pathToMovies ;
	}

	public static String getPathToTVShows()
	{
		return pathToTVShows ;
	}

	public static boolean isTVShowPath( final String inputPath )
	{
		assert( inputPath != null ) ;
		return ((inputPath.contains( "Season " ) && !inputPath.contains( "Season (" )) || inputPath.contains( getTVPathCheckString() ) ) ;
	}

}
