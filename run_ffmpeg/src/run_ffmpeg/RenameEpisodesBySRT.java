package run_ffmpeg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import run_ffmpeg.OpenSubtitles.OpenSubtitles;
import run_ffmpeg.OpenSubtitles.OpenSubtitles_Data;
import run_ffmpeg.OpenSubtitles.OpenSubtitles_SubtitlesResponse;
import run_ffmpeg.TheTVDB.TheTVDB;
import run_ffmpeg.TheTVDB.TheTVDB_episodeClass;
import run_ffmpeg.TheTVDB.TheTVDB_seriesEpisodesClass;

import java.io.File;

public class RenameEpisodesBySRT
{
	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_openaiwhisper.txt" ;
	private static final String stopFileName = "C:\\Temp\\stop_rename_episodes_by_srt.txt" ;

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

		// The number of SRT files we can download is limited by time block (at present 20 downloads per 4-hour block).
		// However, the AI portion of this algorithm takes a long time.
		// -> Download SRT files whenever possible, and work the other actions otherwise.
		// Generalize this to queue all work ahead of time and then do one unit of work each iteration while checking for
		//  download time expiration.
		
		OpenSubtitles openSubtitles = new OpenSubtitles( log, common ) ;
		List< RenameEpisodesBySRT_DownloadDataClass > subtitleDataToDownload = new ArrayList< RenameEpisodesBySRT_DownloadDataClass >() ;
		
		// List of media files from which to extract audio.
		List< File > avFiles = new ArrayList< File >() ;
		
		// The wav files from which to generate SRT files.
		// This structure will be filled as audio is extracted from each av file.
		List< File > wavFiles = new ArrayList< File >() ;
		
		OpenAIWhisper whisper = new OpenAIWhisper( log, common ) ;
		List< File > generatedSRTFiles = new ArrayList< File >() ;
		
		TheTVDB theTVDB = new TheTVDB( log, common ) ;
		
		final List< File > showDirectories = getShowDirectories( Common.getPathToToOCR() ) ;
		for( File showDirectory : showDirectories )
		{
			final String imdbShowIDString = FileNamePattern.getIMDBShowID( showDirectory ) ;
			assert( imdbShowIDString != null ) ;
			
			final String tvdbShowIDString = FileNamePattern.getTVDBShowID( showDirectory ) ;
			assert( tvdbShowIDString != null ) ;
			
			final File[] seasonDirectories = showDirectory.listFiles() ;
			for( File seasonDirectory : seasonDirectories )
			{
				final int seasonNumber = FileNamePattern.getShowSeasonNumber( seasonDirectory ) ;
				final TheTVDB_seriesEpisodesClass seasonEpisodes = theTVDB.getSeriesEpisodesInfo( tvdbShowIDString, Integer.toString( seasonNumber ) ) ;
				
				// Compare the total number of episodes for this season with the srt files already downloaded.
				final Set< Integer > missingDownloadedSRTFiles = findMissingDownloadedSRTEpisodes( imdbShowIDString, seasonDirectory, seasonEpisodes ) ;

				List< OpenSubtitles_Data > allSubtitlesForSeason = new ArrayList< OpenSubtitles_Data >() ;
				
				// Only get subtitle information for the episodes that are missing.
				for( Integer episodeNumberInteger : missingDownloadedSRTFiles )
				{
					OpenSubtitles_SubtitlesResponse response = openSubtitles.searchForSubtitlesByIMDBID(
							imdbShowIDString,
							String.valueOf( seasonNumber ),
							episodeNumberInteger.toString(),
							1 ) ;
					if( null == response )
					{
						log.warning( "null response when searching for subtitles by id " + imdbShowIDString + ", season " + seasonNumber + " episode " + episodeNumberInteger ) ;
						continue ;
					}
					allSubtitlesForSeason.addAll( response.getData() ) ;
				}

//				final List< OpenSubtitles_Data > allSubtitlesForSeason = openSubtitles.getSubtitleInfoForShowSeason( imdbShowIDString, seasonNumber ) ;
				final List< OpenSubtitles_Data > bestSubtitleForEachEpisode = openSubtitles.findBestSubtitleFileIDsToDownloadForSeason( allSubtitlesForSeason ) ;
				for( OpenSubtitles_Data theData : bestSubtitleForEachEpisode )
				{
					final int episodeNumber = theData.getAttributes().getFeature_details().getEpisode_number().intValue() ;
					RenameEpisodesBySRT_DownloadDataClass downloadData = new RenameEpisodesBySRT_DownloadDataClass(
							imdbShowIDString, seasonNumber, episodeNumber, theData, seasonDirectory ) ;
					subtitleDataToDownload.add( downloadData ) ;
				}
				
				// Identify all media files from which to extract audio
				avFiles.addAll( common.getFilesInDirectoryByExtension( seasonDirectory, Common.getVideoExtensions() ) ) ;
			}
		}
		
		// Main loop for processing data items
		// Will only conduct one unit of work per loop to minimize time between subtitle file downloads
		boolean didWorkThisLoop = false ;
		while( shouldKeepRunning() && !subtitleDataToDownload.isEmpty() )
		{
			// This loop is designed to do all srt downloads permitted, followed by one wav extraction,
			//  followed by one ai srt generation. However, so long as any srt files remain to be downloaded,
			//  at most one of wav extraction or ai srt generation will occur per loop. This permits time to
			//  cycle back to the download algorithm to minimize time we miss while the reset time is expired.
			
			// Track if work was accomplished this loop.
			// If no work was accomplished, then we are waiting for the download time to expire.
			didWorkThisLoop = false ;
			
			if( openSubtitles.isDownloadAllowed() )
			{
				// Download next SRT file
				final RenameEpisodesBySRT_DownloadDataClass downloadData = subtitleDataToDownload.removeFirst() ;
				final long downloadResponse = downloadSRTFile( downloadData, openSubtitles ) ;
				if( downloadResponse < 0 )
				{
					// Since this download failed, place it back in the list for a future attempt.
					subtitleDataToDownload.add( 0, downloadData ) ;
					
					didWorkThisLoop = true ;
				}
			}
			
			if( openSubtitles.isDownloadAllowed() && !subtitleDataToDownload.isEmpty() )
			{
				// Can download another srt file. Skip any other work in this loop.
				continue ;
			}
			// PC: Downloads are throttled or no more srt files to download.
			
			// Move on to extracting a .wav file.
			if( !avFiles.isEmpty() )
			{
				final File avFile = avFiles.removeFirst() ;
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
				
				didWorkThisLoop = true ;
			}
			
			if( !didWorkThisLoop && !wavFiles.isEmpty() )
			{
				// Generate an SRT via whisper ai.
				final File wavFile = wavFiles.removeFirst() ;
				final String srtFileNameWithPath = wavFile.getAbsolutePath().replace( ".wav", ".srt" ) ;
				
				File srtFile = new File( srtFileNameWithPath ) ;
				if( !srtFile.exists() )
				{
					// File does not already exist -- create it.
					srtFile = whisper.transcribeToSRT( wavFile ) ;
					didWorkThisLoop = true ;
				}

				if( (srtFile != null) && srtFile.exists() )
				{
					generatedSRTFiles.add( srtFile ) ;
				}
			}
			
			if( !didWorkThisLoop )
			{
				// Nothing done this loop...must be waiting for timer to expire.
				try
				{
					Thread.sleep( 1000 ) ;
				}
				catch( Exception theException )
				{
					log.warning( "Exception during sleep(): " + theException.toString() ) ;
				}
			}
			
		} // while( shouldKeepRunning() )
	} // execute()

	public Set< Integer > findMissingDownloadedSRTEpisodes( final String imdbShowIDString, final File seasonDirectory, final TheTVDB_seriesEpisodesClass seasonEpisodes )
	{
		Set< Integer > missingDownloadedSRTEpisodes = new HashSet< Integer >() ;
		
		for( TheTVDB_episodeClass seasonEpisode : seasonEpisodes.data.episodes )
		{
			final String downloadedSRTFileName = makeSRTDownloadFileName( imdbShowIDString, seasonEpisode.seasonNumber.intValue(), seasonEpisode.number.intValue() ) ;
			final File downloadedSRTFile = new File( seasonDirectory, downloadedSRTFileName ) ;
			if( !downloadedSRTFile.exists() )
			{
				// Missing file.
				missingDownloadedSRTEpisodes.add( seasonEpisode.number ) ;
			}
		}
		return missingDownloadedSRTEpisodes ;
	}
	
	public List< File > getAVFilesFromDirectory( final File seasonDirectory )
	{
		List< File > avFiles = common.getFilesInDirectoryByExtension( seasonDirectory, Common.getVideoExtensions() ) ;
		return avFiles ;
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

	public String makeSRTDownloadFileName( final String imdbShowIDString,
			final int seasonNumber,
			final int episodeNumber )
	{
		final String srtDownloadFileName = imdbShowIDString
				+ " - S" + (seasonNumber < 10 ? "0" : "") + seasonNumber
				+ "E" + (episodeNumber < 10 ? "0" : "" ) + episodeNumber
				+ " - downloaded.srt" ;
		return srtDownloadFileName ;
	}
	
	/**
	 * Download the subtitle file identified by subtitleData into outputDirectory.
	 * @param subtitleData
	 * @param openSubtitles
	 * @param outputDirectory
	 * @return 0 if successful, the next time (in milliseconds) when another download may occur otherwise.
	 */
	public long downloadSRTFile( final RenameEpisodesBySRT_DownloadDataClass downloadData, OpenSubtitles openSubtitles )
	{
		assert( downloadData != null ) ;

		final OpenSubtitles_Data subtitleData = downloadData.subtitleData ;
		final int seasonNumber = downloadData.getSeasonNumber() ;
		final int episodeNumber = downloadData.getEpisodeNumber() ;
		final String subtitleFileID = downloadData.subtitleData.getAttributes().getFiles().getFirst().getFile_id().toString() ;
		
		final String newFileName = makeSRTDownloadFileName( downloadData.getImdbShowIDString(),
				seasonNumber,
				episodeNumber ) ;
		final File newOutputFile = new File( downloadData.getOutputDirectory(), newFileName ) ;
		if( newOutputFile.exists() )
		{
			// File already exists, no need to download.
			return 0 ;
		}
		
		File subtitleFile = openSubtitles.downloadSubtitleFileByID( subtitleFileID, newOutputFile ) ;
		if( null == subtitleFile )
		{
			log.warning( "Failed to download file " + subtitleData.getAttributes().getFiles().getFirst().getFile_name() ) ;
			return -1 ;
		}

		return 0 ;
	}
	
	public void downloadSRTFilesForSeason( final String imdbShowIDString, final int seasonNumber, final File outputDirectory )
	{
		OpenSubtitles openSubtitles = new OpenSubtitles( log, common ) ;

		// First, get all subtitle information for this show and season
		List< OpenSubtitles_Data > allSubtitlesForSeason = openSubtitles.getSubtitleInfoForShowSeason( imdbShowIDString, seasonNumber ) ;
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
			final int episodeNumber = subtitleFileData.getAttributes().getFeature_details().getEpisode_number().intValue() ;
			final String newFileName = makeSRTDownloadFileName( imdbShowIDString, seasonNumber, episodeNumber ) ;
			final File outputFile = new File( outputDirectory, newFileName ) ;

			File subtitleFile = openSubtitles.downloadSubtitleFileByID( subtitleFileID, outputFile ) ;
			if( null == subtitleFile )
			{
				log.warning( "Failed to download file " + subtitleFileData.getAttributes().getFiles().getFirst().getFile_name() ) ;
				continue ;
			}

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
	
	public boolean shouldKeepRunning()
	{
		return !common.shouldStopExecution( getStopFileName() ) ;
	}
	
	public String getStopFileName()
	{
		return stopFileName ;
	}
}
