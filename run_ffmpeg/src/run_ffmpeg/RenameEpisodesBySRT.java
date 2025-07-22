package run_ffmpeg;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableList;

import java.io.File;

public class RenameEpisodesBySRT
{
	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_openaiwhisper.txt" ;

	public RenameEpisodesBySRT()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public RenameEpisodesBySRT( Logger log, Common common )
	{
		this.log = log ;
		this.common = common ;
	}

	public static void main( String[] args )
	{
		(new RenameEpisodesBySRT()).execute() ;
	}

	public void execute()
	{
		common.setTestMode( false ) ;

		final List< File > showDirectories = getShowDirectories( Common.getPathToToOCR() ) ;
		for( File showDirectory : showDirectories )
		{
			final File[] seasonDirectories = showDirectory.listFiles() ;
			for( File seasonDirectory : seasonDirectories )
			{
				final String showIMDBID = FileNamePattern.getIMDBShowID( seasonDirectory ) ;

				final List< File > wavFiles = makeWavFilesForSeason( seasonDirectory ) ;
				final List< File > generatedSRTFiles = makeSRTFilesForSeason( seasonDirectory, wavFiles ) ;
				downloadSRTFilesForSeason( seasonDirectory ) ;
				matchAndRenameShowFilesForSeason( seasonDirectory ) ;
			}
		}
	}

	public List< File > makeWavFilesForSeason( final File seasonDirectory )
	{
		assert( seasonDirectory != null ) ;

		List< File > wavFiles = new ArrayList< File >() ;
		final List< File > avFiles = common.getFilesInDirectoryByExtension( seasonDirectory, Common.getVideoExtensions() ) ;
		for( File avFile : avFiles )
		{
			final String wavFileNameWithPath = Common.replaceExtension( avFile.getAbsolutePath(), "wav" ) ;
			final File wavFile = new File( wavFileNameWithPath ) ;

			if( extractAudioFromAVFile( avFile, wavFile ) )
			{
				wavFiles.add( wavFile ) ;
			}
		}
		return wavFiles ;
	}

	public List< File > makeSRTFilesForSeason( final File seasonDirectory, final List< File > wavFiles )
	{
		assert( seasonDirectory != null ) ;
		assert( wavFiles != null ) ;

		OpenAIWhisper whisper = new OpenAIWhisper( log, common ) ;
		List< File > generatedSRTFiles = new ArrayList< File >() ;

		// Generate an srt file for each file in wavFiles
		for( File wavFile : wavFiles )
		{
			final File srtFile = whisper.transcribeToSRT( wavFile ) ;
			if( srtFile != null )
			{
				generatedSRTFiles.add( srtFile ) ;
			}
		}
		return generatedSRTFiles ;
	}

	public void downloadSRTFilesForSeason( final File seasonDirectory )
	{

	}

	public void matchAndRenameShowFilesForSeason( final File seasonDirectory )
	{

	}

	public List< File > getShowDirectories( final String topLevelDirectoryString )
	{
		assert( topLevelDirectoryString != null ) ;

		List< File > showDirectories = new ArrayList< File >() ;
		final File topLevelDirectoryFile = new File( topLevelDirectoryString ) ;
		final File[] topLevelSubDirectories = topLevelDirectoryFile.listFiles() ;

		// Iterate through the directories.
		// Some of them may be movie directories -- skip those and only keep the show directories.

		// Each show directory has an imdb or tvdb identifier in the path name.
		// Look for one of those.
		final Pattern showTagPattern = Pattern.compile( ".*\\{(imdb|tvdb)-[\\d]+\\}.*Season .*" ) ;

		for( File subDirectory : topLevelSubDirectories )
		{
			// Need to see the full path that includes the "Season XX" directory, which means I need to resolve
			// down to the file level.

			// Find all files under this subdirectory
			final List< File > subDirectoryFiles = common.getFilesInDirectoryByExtension( subDirectory, Common.getVideoExtensions() ) ;
			for( File testFile : subDirectoryFiles )
			{
				final Matcher showTagMatcher = showTagPattern.matcher( testFile.getAbsolutePath() ) ;
				if( showTagMatcher.find() )
				{
					// Found a tv show
					showDirectories.add( subDirectory ) ;
					break ;
				}
			}
		}
		return showDirectories ;
	}

	public boolean extractAudioFromAVFile( final File inputFile, final File outputFile )
	{
		// Extract just the audio
		// The new versions of OpenAI transcription only support mp3, mp4, mpeg, mpga, m4a, wav, and webm
		// For now, let's use .wav
		ImmutableList.Builder< String > ffmpegCommand = new ImmutableList.Builder< String >() ;

		// Setup ffmpeg basic options
		ffmpegCommand.add( common.getPathToFFmpeg() ) ;

		// Overwrite existing files
		ffmpegCommand.add( "-y" ) ;

		// Not exactly sure what these do but it seems to help reduce errors on some files.
		//			ffmpegCommand.add( "-analyzeduration", Common.getAnalyzeDurationString() ) ;
		//			ffmpegCommand.add( "-probesize", Common.getProbeSizeString() ) ;

		// Include source file
		ffmpegCommand.add( "-i", inputFile.getAbsolutePath() ) ;
		ffmpegCommand.add( "-ss", "00:00:00" ) ; // start time
		ffmpegCommand.add( "-t", "00:02:00" ) ; // duration
		ffmpegCommand.add( "-vn" ) ; // disable video
		ffmpegCommand.add( "-sn" ) ; // disable subtitles
		ffmpegCommand.add( "-dn" ) ; // disable data
		ffmpegCommand.add( "-acodec", "pcm_s16le" ) ;
		ffmpegCommand.add( "-ar", "16000" ) ;
		ffmpegCommand.add( "-ac", "1" ) ;
		ffmpegCommand.add( "-y" ) ; // overwrite
		ffmpegCommand.add( outputFile.getAbsolutePath() ) ;

		log.info( common.toStringForCommandExecution( ffmpegCommand.build() ) ) ;
		// Only execute the conversion if testMode is false
		boolean executeSuccess = common.getTestMode() ? true : common.executeCommand( ffmpegCommand ) ;
		if( !executeSuccess )
		{
			log.warning( "Error in execute command" ) ;
		}
		return executeSuccess ;
	}
}
