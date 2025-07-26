package run_ffmpeg;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableList;

import run_ffmpeg.OpenSubtitles.OpenSubtitles;
import run_ffmpeg.OpenSubtitles.OpenSubtitles_Data;

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
			final String showIMDBID = FileNamePattern.getIMDBShowID( showDirectory ) ;
			final File[] seasonDirectories = showDirectory.listFiles() ;
			for( File seasonDirectory : seasonDirectories )
			{
				final int seasonNumber = FileNamePattern.getShowSeasonNumber( seasonDirectory ) ;

				final List< File > wavFiles = makeWavFilesForSeason( seasonDirectory ) ;
				final List< File > generatedSRTFiles = makeSRTFilesForSeason( seasonDirectory, wavFiles ) ;
				downloadSRTFilesForSeason( showIMDBID, seasonNumber, seasonDirectory ) ;
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

			// Only make the wav file if it is absent.
			if( !wavFile.exists() && !common.extractAudioFromAVFile( avFile, wavFile ) )
			{
				log.warning( "Failed to make wav file: " + wavFile.getAbsolutePath() ) ;
				continue ;
			}

			// wav file exists, either through extracting here or finding in the directory
			// Either way, add it to the return list.
			wavFiles.add( wavFile ) ;
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
			final String srtFileNameWithPath = wavFile.getAbsolutePath().replace( ".wav", ".srt" ) ;
			File srtFile = new File( srtFileNameWithPath ) ;
			if( !srtFile.exists() )
			{
				// File does not already exist -- create it.
				srtFile = whisper.transcribeToSRT( wavFile ) ;
			}

			if( srtFile != null )
			{
				generatedSRTFiles.add( srtFile ) ;
			}
		}
		return generatedSRTFiles ;
	}

	public void downloadSRTFilesForSeason( final String imdbShowIDString, final int seasonNumber, final File outputDirectory )
	{
		OpenSubtitles openSubtitles = new OpenSubtitles( log, common ) ;

		// First, get all subtitle information for this show and season
		List< OpenSubtitles_Data > allSubtitlesForSeason = openSubtitles.getSubtitlesForShowSeason( imdbShowIDString, seasonNumber ) ;
		assert( allSubtitlesForSeason != null ) ;

		// Next, find the best subtitles for each episode
		final List< OpenSubtitles_Data > subtitleDataToDownload = openSubtitles.findBestSubtitleFileIDsToDownloadForSeason( allSubtitlesForSeason ) ;
		assert( subtitleDataToDownload != null ) ;

		// Download each subtitle file and rename to match this convention:
		// imdbShowID - SXXEYY - downloaded.srt
		List< File > downloadedSubtitleFiles = new ArrayList< File >() ;
		for( OpenSubtitles_Data subtitleFileData : subtitleDataToDownload )
		{
			final String subtitleFileID = subtitleFileData.getAttributes().getFiles().getFirst().getFile_id().toString() ;
			File subtitleFile = openSubtitles.downloadSubtitleFileByID( subtitleFileID, outputDirectory ) ;
			if( null == subtitleFile )
			{
				log.warning( "Failed to download file " + subtitleFileData.getAttributes().getFiles().getFirst().getFile_name() ) ;
				continue ;
			}
			// Rename
			final int episodeNumber = subtitleFileData.getAttributes().getFeature_details().getEpisode_number().intValue() ;
			final String newFileName = imdbShowIDString
					+ " - "
					+ "S" + (seasonNumber < 10 ? "0" : "") + seasonNumber
					+ "E" + (episodeNumber < 10 ? "0" : "" ) + episodeNumber
					+ " - downloaded.srt" ;
			final File newOutputFile = new File( outputDirectory, newFileName ) ;
			subtitleFile.renameTo( newOutputFile ) ;

			downloadedSubtitleFiles.add( subtitleFile ) ;
		}
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
}
