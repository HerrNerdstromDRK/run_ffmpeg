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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeFrame;
import run_ffmpeg.ffmpeg.FFmpeg_ProbeFrames;
import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;

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

	private static final String[] pathsToWhisperX =
		{
				"c:\\Program Files\\Python\\Python312\\Scripts\\whisperX.exe",
				"d:\\Program Files\\Python\\Python312\\Scripts\\whisperX.exe"
		} ;
	
	protected static final String[] videoExtensions =
		{
				"mkv",
				"mp4",
				"MKV",
				"MP4",
				"MOV"
		} ;

	/// Paths to external applications
	private String pathToFFMPEG = null ;
	private String pathToFFPROBE = null ;
	private String pathToSubtitleEdit = null ;
	private String pathToWhisperX = null ;

	/// The replacement file name for correlated files that are missing. This is for
	/// user interface reporting via the web interface.
	private static final String missingFileSubstituteName = "(none)" ;

	/// The size of an SRT file, in bytes, that represents the minimum valid file length.
	private static final int minimumSRTFileSize = 25 ;

	private static final String analyzeDurationString = "5G" ;
	private static final String probeSizeString = "5G" ;

	/// The name of the primary file server
	private static final String primaryFileServerName = "\\\\skywalker" ;
	private static final String pathToMediaFolderBase = primaryFileServerName + pathSeparator + "Media" ;
	private static final String moviesFolderName = "Movies" ;
	private static final String otherVideosFolderName = "Other_Videos" ;
	private static final String toOCRFolderName = "To_OCR" ;
	private static final String tvShowsFolderName = "TV_Shows" ;
	private static final String pathToMovies = pathToMediaFolderBase + pathSeparator + moviesFolderName ;
	private static final String pathToOtherVideos = pathToMediaFolderBase + pathSeparator + otherVideosFolderName ;
	private static final String pathToToOCR = pathToMediaFolderBase + pathSeparator + toOCRFolderName ;
	private static final String pathToTVShows = pathToMediaFolderBase + pathSeparator + tvShowsFolderName ;
	private static final String pathToTmpDir = pathToMediaFolderBase + pathSeparator + "Test" ;
	private static final String pathToDeleteDir = pathToMediaFolderBase + pathSeparator + "To_Delete" ;

	/// The string to search for in file paths to determine if this is a tv show
	private static final String tvPathCheckString = "TV_Shows" ;

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
		
		for( String testLocation : pathsToWhisperX )
		{
			if( (new File( testLocation )).isFile() )
			{
				// Found the file
				setPathToWhisperX( testLocation ) ;
				break ;
			}
		}
		if( null == getPathToWhisperX() )
		{
			log.warning( "Unable to find whisperX" ) ;
		}

		if( !getIsWindows() )
		{
			// Linux naming
			pathSeparator = "\\\\" ;
		}
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
	 * Extract all audio from the inputFile and place it, as a file, at outputFile.
	 * @param inputFile
	 * @param outputFile
	 * @return
	 */
	public boolean extractAudioFromAVFile( final File inputFile, final File outputFile )
	{
		return extractAudioFromAVFile( inputFile, outputFile, 0, 0, 0, -1, -1, -1 ) ;
	}
	
	/**
	 * Extract the audio from a given media inputFile and place it at the outputFile with given start and duration.
	 * Passing negative values for the durations will result in the entire audio stream being extracted.
	 * @param inputFile
	 * @param outputFile
	 * @param startHours
	 * @param startMinutes
	 * @param startSeconds
	 * @param endHours
	 * @param endMinutes
	 * @param endSeconds
	 * @return
	 */
	public boolean extractAudioFromAVFile( final File inputFile, final File outputFile,
			final int startHours, final int startMinutes, final int startSeconds,
			final int durationHours, final int durationMinutes, final int durationSeconds )
	{
		assert( inputFile != null ) ;
		assert( outputFile != null ) ;

		// Extract just the audio
		// The new versions of OpenAI transcription only support mp3, mp4, mpeg, mpga, m4a, wav, and webm
		// For now, let's use .wav
		ImmutableList.Builder< String > ffmpegCommand = new ImmutableList.Builder< String >() ;

		// Setup ffmpeg basic options
		ffmpegCommand.add( getPathToFFmpeg() ) ;

		// Overwrite existing files
		ffmpegCommand.add( "-y" ) ;

		// Not exactly sure what these do but it seems to help reduce errors on some files.
		//			ffmpegCommand.add( "-analyzeduration", Common.getAnalyzeDurationString() ) ;
		//			ffmpegCommand.add( "-probesize", Common.getProbeSizeString() ) ;

		// Include source file
		ffmpegCommand.add( "-i", inputFile.getAbsolutePath() ) ;

		if( (durationHours != -1) && (durationMinutes != -1) && (durationSeconds != -1) )
		{
			final String startTime = String.format( "%02d:%02d:%02d", startHours, startMinutes, startSeconds ) ;
			final String duration = String.format( "%02d:%02d:%02d", durationHours, durationMinutes, durationSeconds ) ;

			ffmpegCommand.add( "-ss", startTime ) ; // start time
			ffmpegCommand.add( "-t", duration ) ; // duration
		}
		ffmpegCommand.add( "-vn" ) ; // disable video
		ffmpegCommand.add( "-sn" ) ; // disable subtitles
		ffmpegCommand.add( "-dn" ) ; // disable data
		ffmpegCommand.add( "-acodec", "pcm_s16le" ) ;
		ffmpegCommand.add( "-ar", "16000" ) ;
		ffmpegCommand.add( "-ac", "1" ) ;
		ffmpegCommand.add( "-y" ) ; // overwrite
		ffmpegCommand.add( outputFile.getAbsolutePath() ) ;

		log.info( toStringForCommandExecution( ffmpegCommand.build() ) ) ;
		// Only execute the conversion if testMode is false
		boolean executeSuccess = getTestMode() ? true : executeCommand( ffmpegCommand ) ;
		if( !executeSuccess )
		{
			log.warning( "Error in execute command" ) ;
		}
		return executeSuccess ;
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
		} ;

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
	public FFmpeg_ProbeResult ffprobeFile( File theFile, Logger log )
	{	
		log.fine( "Processing: " + theFile.getAbsolutePath() ) ;
		FFmpeg_ProbeResult result = null ;

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
				result = gson.fromJson( inputBuffer, FFmpeg_ProbeResult.class ) ;

				result.setFileNameWithPath( theFile.getAbsolutePath() ) ;
				result.setFileNameWithoutPath( theFile.getName() ) ;
				result.setFileNameShort( shortenFileName( theFile.getAbsolutePath() ) ) ;
				result.setProbeTime( System.currentTimeMillis() ) ;
				result.setSize( theFile.length() ) ;
				result.setLastModified( theFile.lastModified() ) ;
				log.fine( result.toString() ) ;
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
	public FFmpeg_ProbeResult ffprobeFile( TranscodeFile theFile, Logger log )
	{
		FFmpeg_ProbeResult probeResult = ffprobeFile( theFile.getInputFile(), log ) ;
		if( probeResult != null )
		{
			theFile.processFFmpegProbeResult( probeResult ) ;
		}
		return probeResult ;
	}

	public List< FFmpeg_ProbeFrame > ffprobe_getVideoFrames( File theFile, Logger log )
	{
		return ffprobe_getVideoFrames( theFile, log, false ) ;
	}

	/**
	 * Get the Group of Pictures result from a given file.
	 * @param theFile
	 * @param log
	 * @param minimalOnly: If true, then only return the frame and 'key_frame' flag.
	 * @return
	 */
	public List< FFmpeg_ProbeFrame > ffprobe_getVideoFrames( File theFile, Logger log, boolean minimalOnly )
	{	
		log.fine( "Processing: " + theFile.getAbsolutePath() ) ;
		FFmpeg_ProbeFrames result = null ;

		ImmutableList.Builder< String > ffprobeExecuteCommand = new ImmutableList.Builder< String >() ;
		ffprobeExecuteCommand.add( getPathToFFprobe() ) ;

		// Add option "-v quiet" to suppress the normal ffprobe output
		ffprobeExecuteCommand.add( "-select_streams", "v:0" ) ;
		ffprobeExecuteCommand.add( "-show_frames" ) ;

		if( minimalOnly )
		{
			ffprobeExecuteCommand.add( "-show_entries", "frame=key_frame" ) ;
		}

		//		ffprobeExecuteCommand.add( "-skip_frame", "nokey" ) ;
		ffprobeExecuteCommand.add( "-print_format", "json" ) ;

		// Finally, add the input file
		ffprobeExecuteCommand.add( theFile.getAbsolutePath() ) ;

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
				// Deserialize the JSON frames info from this file
				result = gson.fromJson( inputBuffer, FFmpeg_ProbeFrames.class ) ;
			}
		}
		catch( Exception theException )
		{
			theException.printStackTrace() ;
		}
		return result.frames ;
	}

	/**
	 * Return the extension, without '.', of the given filename.
	 * @param fileNameWithExtension
	 * @return
	 */
	public static String getExtension( final String fileNameWithExtension )
	{
		assert( fileNameWithExtension != null ) ;
		assert( fileNameWithExtension.contains( "." ) ) ;
		assert( fileNameWithExtension.length() >= 5 ) ;

		final String extension = fileNameWithExtension.substring( fileNameWithExtension.length() - 3 ) ;
		return extension ;
	}

	public static String getFileNameWithoutExtension( final File inputFile )
	{
		final String fileName = inputFile.getName() ;
		final String extension = getExtension( fileName ) ;
		final String fileNameWithoutExtension = fileName.replace( "." + extension, "" ) ;
		return fileNameWithoutExtension ;
	}

	/**
	 * Return the files in the inputDirectory that match the given pattern. The pattern will be matched against the file name and extension but not the path.
	 * @param inputDirectory
	 * @param fileMatchPattern
	 * @return
	 */
	public List< File > getFilesInDirectoryByRegex( final File inputDirectory, final Pattern fileMatchPattern )
	{
		assert( inputDirectory != null ) ;
		assert( inputDirectory.isDirectory() ) ;
		assert( fileMatchPattern != null ) ;
		
		List< File > matchingFiles = new ArrayList< File >() ;
		final File[] filesInDirectory = inputDirectory.listFiles() ;
		
		for( File theFile : filesInDirectory )
		{
			final Matcher fileMatchMatcher = fileMatchPattern.matcher( theFile.getName() ) ;
			if( fileMatchMatcher.find() )
			{
				// Found a match.
				matchingFiles.add( theFile ) ;
			}
		}
		return matchingFiles ;		
	}
	
	public List< File > getFilesInDirectoryByRegex( final File inputDirectory,final String fileMatchPatternString )
	{
		assert( fileMatchPatternString != null ) ;
		
		return getFilesInDirectoryByRegex( inputDirectory, Pattern.compile( fileMatchPatternString ) ) ;
	}
	
	/**
	 * Return a list of Files in the given directory with any of the given extensions.
	 * @param inputDirectory
	 * @param inputExtensions
	 * @return non-null, but perhaps empty
	 */
	public List< File > getFilesInDirectoryByExtension( final File inputDirectoryFile, final String inputExtensions )
	{
		assert( inputDirectoryFile != null ) ;
		return getFilesInDirectoryByExtension( inputDirectoryFile.getAbsolutePath(), inputExtensions ) ;
	}

	/**
	 * Return a list of Files in the given directory with any of the given extensions.
	 * @param inputDirectory
	 * @param inputExtensions
	 * @return non-null, but perhaps empty
	 */
	public List< File > getFilesInDirectoryByExtension( final File inputDirectoryFile, final String[] inputExtensions )
	{
		assert( inputDirectoryFile != null ) ;
		return getFilesInDirectoryByExtension( inputDirectoryFile.getAbsolutePath(), inputExtensions ) ;
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
		return pathSeparator ;
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

	public static String[] getVideoExtensions()
	{
		return videoExtensions ;
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
				System.out.println( "Common.setupLogger> Unable to create logger FileHandler as file "
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

	public NumberFormat getNumberFormat()
	{
		return numFormat ;
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

	public static String getPathToTmpDir()
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

	public String getPathToWhisperX()
	{
		return pathToWhisperX ;
	}

	public void setPathToWhisperX( final String pathToWhisperX )
	{
		this.pathToWhisperX = pathToWhisperX ;
	}
	
	public void setTestMode( boolean newValue )
	{
		testMode = newValue ;
	}

	/**
	 * Returns a copy of the string without extension or '.'.
	 * Assumes extension is three characters.
	 * @param fileNameWithExtension
	 * @return
	 */
	public static String stripExtensionFromFileName( final String fileNameWithExtension )
	{
		assert( fileNameWithExtension != null ) ;
		assert( fileNameWithExtension.contains( "." ) ) ;

		final String fileNameWithoutExtension = fileNameWithExtension.substring( 0, fileNameWithExtension.length() - 4 ) ;
		return fileNameWithoutExtension ;
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

	public static String getTVPathCheckString()
	{
		return tvPathCheckString ;
	}

	public static List< String > getAllMediaFolders()
	{
		List< String > allMediaFolders = new ArrayList< String >() ;
		allMediaFolders.add( getPathToMovies() ) ;
		allMediaFolders.add( getPathToTVShows() ) ;
		//		allMediaFolders.add( getPathToOtherVideos() ) ;
		return allMediaFolders ;
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

	public static String getPathToMediaFolderBase()
	{
		return pathToMediaFolderBase ;
	}

	public static String getPathToOtherVideos()
	{
		return pathToOtherVideos ;
	}

	public static String getPathToToOCR()
	{
		return pathToToOCR ;
	}

	public static String getPathToDeleteDir()
	{
		return pathToDeleteDir ;
	}

	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}

	public static void log_info( final String message, Logger log, final List<? extends Object > theList )
	{
		log.info( message ) ;
		for( Object theObject : theList )
		{
			log.info( theObject.toString() ) ;
		}
	}

}
