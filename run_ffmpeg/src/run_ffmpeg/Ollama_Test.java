package run_ffmpeg;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.CosineDistance;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.OllamaOptions;

import run_ffmpeg.TheTVDB.TheTVDB;
import run_ffmpeg.TheTVDB.TheTVDB_episodeClass;
import run_ffmpeg.TheTVDB.TheTVDB_seriesEpisodesClass;

public class Ollama_Test
{
	/// Setup the logging subsystem
	protected Logger log = null ;
	protected Common common = null ;
	protected TheTVDB theTVDB = null ;
	protected SRTFileUtils srtFileUtils = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_ollama_test.txt" ;
	//	private static final String stopFileName = "C:\\Temp\\stop_ollama_test.txt" ;

	public Ollama_Test( Logger log, Common common )
	{
		this.log = log ;
		this.common = common ;

		initObject() ;
	}

	public Ollama_Test()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		initObject() ;
	}

	private void initObject()
	{
		log.info( "Logging into tvdb" ) ;
		theTVDB = new TheTVDB( log, common ) ;
		srtFileUtils = new SRTFileUtils( log, common ) ;
	}

	public static void main( final String[] args )
	{
		(new Ollama_Test()).execute() ;
	}

	public void execute()
	{
		//		Properties config = new Properties() ;
		//		config.setProperty( "spring.ai.ollama.init.pull-model-strategy", PullModelStrategy. ) ;

		//		final File seasonDirectory = new File( "\\\\skywalker\\Media\\To_OCR\\Game Of Thrones (2011) {imdb-944947} {tvdb-121361} {edition-4K}\\Season 01" ) ;
		final File seasonDirectory = new File( "\\\\skywalker\\Media\\To_OCR\\Arrested Development (2003) {imdb-0367279} {tvdb-72173}\\Season 03" ) ;
//		final String showName = FileNamePattern.getShowName_static( seasonDirectory ) ;
		final String tvdbShowIDString = FileNamePattern.getTVDBShowID( seasonDirectory ) ;
//		final int showSeasonNumberInt = FileNamePattern.getShowSeasonNumber( seasonDirectory ) ;
//		final String showSeasonNumberString = Integer.toString( showSeasonNumberInt ) ;

		final File inputFile = new File( seasonDirectory, "ARRESTED_DEVELOPMENT_S2D1-B1_t00.en.srt" ) ;
//		final String inputFileData = srtFileUtils.getFirstMinutesOfSRTFile( inputFile, 1000 ) ;
		//		log.info( "inputFileData: " + inputFileData   ) ;

		// Pull information about each episode in the season.
		final TheTVDB_seriesEpisodesClass seasonEpisodes = theTVDB.getSeriesEpisodesInfo( tvdbShowIDString,
				Integer.toString( FileNamePattern.getShowSeasonNumber( inputFile ) ) ) ;

		final List< File > downloadedSRTFiles = common.getFilesInDirectoryByRegex( seasonDirectory, "[\\d]+ - S[\\d]+E[\\d]+ - downloaded.srt" ) ;
		final List< File > mkvFiles = common.getFilesInDirectoryByRegex( seasonDirectory, "^(?!.*downloaded).*\\.mkv$" ) ;

		// Find the minimum duration of the episodes as measured by thetvdb.
		double minEpisodeDuration = seasonEpisodes.getMinDuration() ;

		// Check if this is a half-hour or hour show.
		// All durations are in seconds.
		if( minEpisodeDuration <= (30 * 60) )
		{
			// Half-hour show.
			// Since thetvdb is notoriously inaccurate (may show 25 minutes for min duration of shows that are actually 20 minutes long), adjust
			// the min duration to account for actual video length.
			minEpisodeDuration = 20 * 60 ;
		}
		else
		{
			// Hour-long show.
			minEpisodeDuration = 45 * 60 ;
		}

		final List< File > mkvFilesPruned = common.pruneByMinDuration( mkvFiles,  minEpisodeDuration ) ;
		
		// Now that I have the list of mkvFiles that should be episodes, get the srt file for each.
		// Structure will be Pair< mkvFile, srtFile >.
		List< Pair< File, File > > mkvAndGeneratedSRTFiles = new ArrayList< Pair< File, File > >() ;
		for( File mkvFile : mkvFilesPruned )
		{
			final String srtFileName = mkvFile.getName().replace( ".mkv", ".en.srt" ) ;
			final File srtFile = new File( mkvFile.getParentFile(), srtFileName ) ;
			if( !srtFile.exists() )
			{
				log.warning( "No srt file for mkv file: " + mkvFile ) ;
			}
			else
			{
				mkvAndGeneratedSRTFiles.add( Pair.of( mkvFile, srtFile ) ) ;
			}
		}
		// PC: Now have the mkv files and their associated srt files, and the download srt files for the season episodes.

		// Read the downloaded srt files into memory.
//		final List< Pair< File, String > > downloadedSRTs = srtFileUtils.readSRTFiles( downloadedSRTFiles ) ;

		OllamaApi oApi = OllamaApi.builder().build() ;

		OllamaChatModel chatModel = OllamaChatModel.builder().ollamaApi( oApi ).defaultOptions(
				OllamaOptions.builder()
				//.model( OllamaModel.LLAMA3_2 )
//				.model( OllamaModel.LLAVA )
				.model( OllamaModel.MISTRAL )
				.temperature( 0.3 )
				.build() )
				.build() ;
		
		for( Pair< File, File > mkvSRTPair : mkvAndGeneratedSRTFiles )
		{
//			final File mkvFile = mkvSRTPair.getLeft() ;
			final File srtFile = mkvSRTPair.getRight() ;
			
			matchGeneratedSRTWithBestDownloadedSRT_withBioInformatics( chatModel, srtFile, downloadedSRTFiles ) ;
		}
		
		// Indexed by episode info
//		final List< String > episodesInfo = generateSeasonsEpisodeInfo( seasonEpisodes, tvdbShowIDString, showSeasonNumberInt ) ;
//		final Map< TheTVDB_episodeClass, String > summaries = generateSeasonSummaries(  seasonEpisodes, chatModel, seasonDirectory, tvdbShowIDString, showSeasonNumberInt ) ;
//
//		matchSubtitleFileWithBestSummary( chatModel, inputFile, seasonEpisodes, summaries ) ;

		//		// Or with streaming responses
		//		final Flux< ChatResponse > fluxResponse = chatModel.stream( new Prompt( "Generate the names of 5 famous pirates." ) ) ;
		//		log.info( "fluxResponse: " + fluxResponse.toString() ) ;
	}
	
	public void matchGeneratedSRTWithBestDownloadedSRT_withAI( OllamaChatModel chatModel,
			final File generatedSRTFile,
			final List< File > downloadedSRTFiles )
	{
		assert( chatModel != null ) ;
		assert( generatedSRTFile != null ) ;
		assert( generatedSRTFile.exists() ) ;
		assert( downloadedSRTFiles != null ) ;
		
		// Now ask the bot to match the input file with an episode summary.
//		String promptString = "You are tasked with identifying which from a selection of downloaded television show episode subtitle transcripts most accurately describes"
//				+ " a subtitle transcript taken from the actual television show episode."
//				+ " I will provide you with the authoritative episode transcript and multiple downloaded subtitle transcriptions of episodes of the same television"
//				+ " show from the same season. Your job is to perform a contextual match between the authoritative transcript and the downloaded transcripts"
//				+ " and identify which downloaded transcript most closely contextually matches the authoritative transcript."
//				+ " Here is the authoritative transcript of one episode of the television show, encased in curly braces: {"
//				+ getFirstMinutesOfSRTFile( generatedSRTFile, 1000 ) + "}."
//				+ " Here are the downloaded subtitle transcripts of the episodes of the same television show from the same season."
//				+ " Each downloaded transcript is prefixed with its episode number and encased with curly brackets. " ;
//		promptString = StringUtils.normalizeSpace( promptString ) ;
//		promptString = promptString.replace( "<i>", "" ).replace( "</i>", "" ).replace( "[", System.lineSeparator() + "[" ) ;
		
		String promptString = "Objective: "
				+ "Compare the subtitle transcript extracted from a TV show media file against a set of downloaded subtitle transcripts that "
				+ "belong to episodes of the same season. Identify which downloaded transcript best matches the media file transcript and explain why (briefly). " ;
		promptString += "Input format: "
				+ "Media-file transcript: {input} "
				+ "Candidate transcripts: Transcript 1: {input1}; Transcript 2: {input2}; ... " ;
		promptString += "Normalize each transcript: "
				+ "Strip or ignore timestamps, "
				+ "Convert all text to lower-case, "
				+ "Remove leading/trailing whitespace, extra spaces, and any non-dialogue lines (e.g., [Music], [Laughs]); " ;
		promptString += "Tokenize the cleaned transcripts into words (you may collapse consecutive whitespace); " ;
		promptString += "Compute similarity between the media file transcript and each candidate: "
				+ "Use a combination of metrics (e.g., Levenshtein distance, Jaccard similarity, and cosine similarity on TF-IDF vectors), "
				+ "Weight them to give more importance to word order (Levenshtein) and to overall overlap (Jaccard); " ;
		promptString += "Rank the candidates by overall similarity score (higher is better). " ;
		promptString += "Output: "
				+ "The name of the best matching transcript, "
				+ "The similarity scores for each candidate (rounded to 3 decimal places), "
				+ "A brief justification (<= 50 words) of why the best match is superior (e.g., higher word overlap, lower edit distance). " ;
		
		promptString += "Media-file transcript: {"
				+ srtFileUtils.getFirstMinutesOfSRTFile( generatedSRTFile, 1000 )
				+ "} " ;
		promptString += "Candidate transcripts: " ;
		
		boolean firstTranscript = true ;
		for( File downloadedSRTFile : downloadedSRTFiles )
		{
			final Pattern episodeNumberPattern = Pattern.compile( "[\\d]+ - S[\\d]{2}E(?<episodeNumber>[\\d]{2}) - downloaded.srt" ) ;
			final Matcher episodeNumberMatcher = episodeNumberPattern.matcher( downloadedSRTFile.getName() ) ;
			if( !episodeNumberMatcher.find() )
			{
				log.warning( "Invalid pattern for seasonNumber in filename " + downloadedSRTFile.getName() ) ;
				continue ;
			}
			// PC: Valid pattern
			final String episodeNumberString = episodeNumberMatcher.group( "episodeNumber" ) ;
			final int episodeNumberInt = Integer.valueOf( episodeNumberString ) ;
//			log.info( "episodeNumberInt: " + episodeNumberInt + " for file " + downloadedSRTFile.getName() ) ;
			
			final String downloadedSRTFileContent = srtFileUtils.getFirstMinutesOfSRTFile( downloadedSRTFile,  1000 ) ;
			
			if( !firstTranscript )
			{
				promptString += "; " ;
			}
			firstTranscript = false ;
			
			promptString += "Transcript " + episodeNumberInt + ": {" +  downloadedSRTFileContent + "}" ;
			promptString += System.lineSeparator() ;
		}

//		promptString +=  "Compare the provided authoritative episode transcript against each of the downloaded episode transcripts."
//				+ " Identify and report the episode number from the downloaded transcripts that best matches the authoritative transcript."
//				+ " Provide a brief explanation of your reasoning, highlighting specific similarities and differences that increased or reduced the matching confidence."
//				+ " Output your answer in the following format:"
//				+ " Matching Downloaded Transcript Episode Number: [Episode Number]"
//				+ " Reasoning: [Explanation of the match, referencing key elements the authoritative and downloaded subtitle content]." ;

		// Do some cleanup.
		promptString = promptString.replace( "<i>", "" ).replace( "</i>", "" ).replace( "[", System.lineSeparator() + "[" ) ;
//		promptString = StringUtils.normalizeSpace( promptString ) ;

//		log.info( "promptString.length(): " + promptString.length() ) ;
		log.fine( "promptString: " + promptString ) ;
		Prompt matchingPrompt = new Prompt( promptString ) ;

		log.info( "Performing matching of file " + generatedSRTFile.getName() ) ;
		//		log.info( "Prompt string: " + promptString ) ;
		final ChatResponse matchingResponse = chatModel.call( matchingPrompt ) ;
		log.info( "matchingResponse: " + matchingResponse.getResult().getOutput().getText().trim() ) ;
	}
	
	public void matchGeneratedSRTWithBestDownloadedSRT_withBioInformatics( OllamaChatModel chatModel,
			final File generatedSRTFile,
			final List< File > downloadedSRTFiles )
	{
		assert( chatModel != null ) ;
		assert( generatedSRTFile != null ) ;
		assert( generatedSRTFile.exists() ) ;
		assert( downloadedSRTFiles != null ) ;

		final String generatedSRTFileData = srtFileUtils.getFirstMinutesOfSRTFile( generatedSRTFile, 1000 ) ;
		final String normalizedGeneratedSRTFileData = srtFileUtils.normalizeSRTData( generatedSRTFileData ) ;
		Pair< Double, File > bestDownloadedMatch = Pair.of( Double.MAX_VALUE, null ) ;
		
		for( File downloadedSRTFile : downloadedSRTFiles )
		{
//			final Pattern episodeNumberPattern = Pattern.compile( "[\\d]+ - S[\\d]{2}E(?<episodeNumber>[\\d]{2}) - downloaded.srt" ) ;
//			final Matcher episodeNumberMatcher = episodeNumberPattern.matcher( downloadedSRTFile.getName() ) ;
//			if( !episodeNumberMatcher.find() )
//			{
//				log.warning( "Invalid pattern for seasonNumber in filename " + downloadedSRTFile.getName() ) ;
//				continue ;
//			}
			// PC: Valid pattern
//			final String episodeNumberString = episodeNumberMatcher.group( "episodeNumber" ) ;
//			final int episodeNumberInt = Integer.valueOf( episodeNumberString ) ;
//			log.info( "episodeNumberInt: " + episodeNumberInt + " for file " + downloadedSRTFile.getName() ) ;
			
			final String downloadedSRTFileData = srtFileUtils.getFirstMinutesOfSRTFile( downloadedSRTFile,  1000 ) ;
			final String normalizedDownloaedSRTFileData = srtFileUtils.normalizeSRTData( downloadedSRTFileData ) ;
			
			// Compare this downloadedSRTFile with the generated SRT file
			CosineDistance cosineDistance = new CosineDistance() ; // Lower is better
			final Double cosineDistanceDouble = cosineDistance.apply( normalizedGeneratedSRTFileData, normalizedDownloaedSRTFileData ) ;

			if( (null == bestDownloadedMatch.getRight()) || (cosineDistanceDouble.doubleValue() < bestDownloadedMatch.getLeft()) )
			{
				// No matches yet or this is a better match.
				bestDownloadedMatch = Pair.of( cosineDistanceDouble, downloadedSRTFile ) ;
			}
		} // for( downloadedSRTFile )
		log.info( "Best match for " + generatedSRTFile.getName() + " is " + bestDownloadedMatch.getRight().getName() ) ;
	}

	public void matchSubtitleFileWithBestSummary(
			OllamaChatModel chatModel,
			final File inputFile,
			final TheTVDB_seriesEpisodesClass seasonEpisodes,
			final Map< TheTVDB_episodeClass, String > summaries )
	{
		assert( chatModel != null ) ;
		assert( inputFile != null ) ;
		assert( inputFile.exists() ) ;
		assert( seasonEpisodes != null ) ;
		assert( summaries != null ) ;

		final String inputFileData = srtFileUtils.getFirstMinutesOfSRTFile( inputFile, 1000 ) ;

		// Now ask the bot to match the input file with an episode summary.
		String promptString = "You are tasked with identifying which of the following episode summaries most accurately describes the provided episode dialogue."
				+ "Here is the full dialogue transcript of one episode of a television show: ["
				+ inputFileData + "]. "
				+ "Here are the summaries of all the episodes in the season. Each summary is prefixed with its episode number and episode name: " ;
		for( Map.Entry< TheTVDB_episodeClass, String > entry : summaries.entrySet() )
		{
			final TheTVDB_episodeClass episodeInfo = entry.getKey() ;
			final String summary = entry.getValue() ;

			promptString += "Episode " + episodeInfo.number + ", Name: " + episodeInfo.name + ": [" + summary + "]. " ;
		}

		promptString +=  "Compare the provided episode dialogue against each of the spidoe summaries."
				+ " Identify and report the episode number that best matches the events, key plot points, and character interactions described in the dialogue."
				+ " Provide a brief explanation of your reasoning, highlighting specific plot points or dialogue exchagnes that align with the chosen summary."
				+ " Output your answer in the following format: "
				+ " Matching Episode: [Episode Number]"
				+ " Reasoning: [Explanation of the match, referencing key elements from the dialogue and summary]." ;

		Prompt matchingPrompt = new Prompt( promptString ) ;

		log.info( "Performing matching of file " + inputFile.getName() ) ;
		//		log.info( "Prompt string: " + promptString ) ;
		final ChatResponse matchingResponse = chatModel.call( matchingPrompt ) ;
		log.info( "matchingResponse: " + matchingResponse.getResult().getOutput().getText().trim() ) ;
	}

	public Map< TheTVDB_episodeClass, String > generateSeasonSummaries(
			final TheTVDB_seriesEpisodesClass seasonEpisodes,
			final OllamaChatModel chatModel,
			final File seasonDirectory,
			final String tvdbShowIDString,
			final int seasonNumber )
	{
		assert( chatModel != null ) ;
		assert( seasonDirectory != null ) ;
		assert( tvdbShowIDString != null ) ;
		assert( seasonNumber > 0 ) ;

		// Indexed by episode info
		Map< TheTVDB_episodeClass, String > summaries = new HashMap< TheTVDB_episodeClass, String >() ;
		for( TheTVDB_episodeClass seasonEpisode : seasonEpisodes.data.episodes )
		{
			final String episodeName = seasonEpisode.name ;
			final Integer episodeNumber = seasonEpisode.number ;

			summaries.put( seasonEpisode, getSummary( seasonDirectory, episodeNumber.intValue(), episodeName, chatModel ) ) ;
		}

		return summaries ;
	}

	public List< String > generateSeasonsEpisodeInfo( final TheTVDB_seriesEpisodesClass seasonEpisodes,
			final String tvdbShowIDString,
			final int seasonNumber  )
	{
		assert( tvdbShowIDString != null ) ;
		assert( seasonNumber > 0 ) ;

		// Indexed by episode info
		List< String > seasonEpisodesInfo = new ArrayList< String >() ;
		for( TheTVDB_episodeClass seasonEpisode : seasonEpisodes.data.episodes )
		{
			final String episodeName = seasonEpisode.name ;
			final Integer episodeNumber = seasonEpisode.number ;
			final Integer runTime = seasonEpisode.runtime ;

			seasonEpisodesInfo.add( "Episode Number: " + episodeNumber ) ;
			seasonEpisodesInfo.add( "Episode Name: " + episodeName ) ;
			seasonEpisodesInfo.add( "Episode Run Time: " + runTime ) ;
			seasonEpisodesInfo.add( "***" ) ;
		}
		return seasonEpisodesInfo ;
	}

	/**
	 * Return the summary for the given episode number. Assumes episodeNum is valid. It will first look for a file, otherwise pull from the AI bot.
	 * @param episodeNum
	 * @return
	 */
	public String getSummary( final File inputDir, final int episodeNum, final String episodeName, OllamaChatModel chatModel )
	{
		String summary = null ;
		final String fileName = "Summary - Episode " + episodeNum + ".txt" ;
		File dataFile = new File( inputDir, fileName ) ;

		if( dataFile.exists() )
		{
			try
			{
				log.info( "Reading from file " + dataFile.getAbsolutePath() ) ;
				summary = Files.readString( dataFile.toPath() ) ;
			}
			catch( Exception theException )
			{
				log.warning( "Unable to read data from file " + dataFile.getAbsolutePath() + ": " + theException.toString() ) ;
			}
		}

		final String showName = FileNamePattern.getShowName_static( inputDir ) ;
		final int showSeasonNumberInt = FileNamePattern.getShowSeasonNumber( inputDir ) ;

		if( null == summary )
		{
			// No file found.
			// Generate a summary.
			log.info( "Getting summary information for " + showName + ", S" + showSeasonNumberInt + "E" + episodeNum + " via chat bot...") ;
			final String promptString =  "Provide a summary of "
					+ showName
					+ " Season " + showSeasonNumberInt
					+ " Episode " + episodeNum
					+ " entitled \"" + episodeName + "\""
					+ " with enough detail and unique elements that a subsequent matching using an artificial intelligence algorithm"
					+ " against a full dialogue listing of the episode can accurately match the dialogue to a specific episode with high confidence." ;
			final ChatResponse chatResponse = chatModel.call( new Prompt( promptString ) ) ;
			summary = chatResponse.getResult().getOutput().getText().trim() ;
			log.info( "Get summary: " + summary ) ;

			// Write the summary to the data file
			try
			{
				log.info( "Writing summary to file " + dataFile.getAbsolutePath() ) ;
				Files.writeString( dataFile.toPath(), summary ) ;
			}
			catch( Exception theException )
			{
				log.warning( "Error writing to file " + fileName + ": " + theException.toString() ) ;
			}
		}
		return summary ;
	}
}
