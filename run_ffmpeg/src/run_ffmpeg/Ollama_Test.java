package run_ffmpeg;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;

import reactor.core.publisher.Flux;
import run_ffmpeg.TheTVDB.TheTVDB;
import run_ffmpeg.TheTVDB.TheTVDB_episodeClass;
import run_ffmpeg.TheTVDB.TheTVDB_seriesEpisodesClass;

public class Ollama_Test
{
	/// Setup the logging subsystem
	protected Logger log = null ;
	protected Common common = null ;
	protected TheTVDB theTVDB = null ;

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
	}

	public static void main( final String[] args )
	{
		(new Ollama_Test()).execute() ;
	}

	public void execute()
	{
		//		Properties config = new Properties() ;
		//		config.setProperty( "spring.ai.ollama.init.pull-model-strategy", PullModelStrategy. ) ;

		final String tvdbShowIDString = "121361" ;
		final File seasonDirectory = new File( "\\\\skywalker\\Media\\To_OCR\\Game Of Thrones (2011) {imdb-944947} {tvdb-121361} {edition-4K}\\Season 01\\" ) ;
		final File inputFile = new File( seasonDirectory, "Game Of Thrones Season 1 Disc 1_t02.en.srt" ) ;
		final String inputFileData = getFirstMinutesOfSRTFile( inputFile, 1000 ) ;
		//		log.info( "inputFileData: " + inputFileData   ) ;

		OllamaApi oApi = OllamaApi.builder().build() ;

		OllamaChatModel chatModel = OllamaChatModel.builder().ollamaApi( oApi ).defaultOptions(
				OllamaOptions.builder()
				.model( OllamaModel.LLAMA3_2 )
				//							.model( OllamaModel.QWQ )
				.temperature( 0.3 )
				.build() )
				.build() ;
		
		final TheTVDB_seriesEpisodesClass seasonEpisodes = theTVDB.getSeriesEpisodesInfo( tvdbShowIDString, "1" ) ;
		
		// Indexed by episode info
		Map< TheTVDB_episodeClass, String > summaries = new HashMap< TheTVDB_episodeClass, String >() ;
		for( TheTVDB_episodeClass seasonEpisode : seasonEpisodes.data.episodes )
		{
			final String episodeName = seasonEpisode.name ;
			final Integer episodeNumber = seasonEpisode.number ;
			
			summaries.put( seasonEpisode, getSummary( seasonDirectory, episodeNumber.intValue(), episodeName, chatModel ) ) ;
		}

		// Got the information for each episode.
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
			+ " Identify the episode summary that best matches the events, key plot points, and character interactions described in the dialogue."
			+ " Provide a brief explanation of your reasoning, highlighting specific plot points or dialogue exchagnes that align with the chosen summary."
			+ " Output your answer in the following format: "
			+ " Matching Episode: [Episode Number]"
			+ " Reasoning: [Explanation of the match, referencing key elements from the dialogue and summary]." ;
		
		Prompt matchingPrompt = new Prompt( promptString ) ;
		
		log.info( "Performing matching of file " + inputFile.getName() ) ;
		final ChatResponse matchingResponse = chatModel.call( matchingPrompt ) ;
		log.info( "matchingResponse: " + matchingResponse.getResult().getOutput().getText().trim() ) ;
		
//		log.info( "Sending chat request..." ) ;
//		final ChatResponse chatResponse = chatModel.call(
//				new Prompt( "Provide a summary of Game of Thrones Season 01 Episode 02" ) ) ;

		//		final ChatResponse chatResponse = chatModel.call(
		//				new Prompt( "You are tasked with identifying which of the following episode summaries most "
		//				+ "accurately describes the provided episode dialogue."
		//				+ "Here is the full dialogue transcript of one episode of a television show: ["
		//				+ inputFileData + "]. "
		//				+ " Episode 1: "
		//				+ "[Jon Arryn, the Hand of the King, is dead. King Robert Baratheon plans to ask his oldest friend, Eddard Stark, to take Jon's place. Across the sea, Viserys Targaryen plans to wed his sister to a nomadic warlord in exchange for an army.]"
		//				+ " Episode 2: "
		//				+ "[While Bran recovers from his fall, Ned takes only his daughters to Kings Landing. Jon Snow goes with his uncle Benjen to The Wall. Tyrion joins them.]"
		//				+ " Episode 3: "
		//				+ "[Lord Stark and his daughters arrive at King's Landing to discover the intrigues of the king's realm.]"
		//				+ " Episode 4: "
		//				+ "[Eddard investigates Jon Arryn's murder. Jon befriends Samwell Tarly, a coward who has come to join the Night's Watch.]"
		//				+ " Episode 5: "
		//				+ "[Catelyn has captured Tyrion and plans to bring him to her sister, Lysa Arryn, at The Vale, to be tried for his, supposed, crimes against Bran. Robert plans to have Daenerys killed, but Eddard refuses to be a part of it and quits.]"
		//				+ " Episode 6: "
		//				+ "[While recovering from his battle with Jamie, Eddard is forced to run the kingdom while Robert goes hunting. Tyrion demands a trial by combat for his freedom. Viserys is losing his patience with Drogo.]"
		//				+ " Episode 7: "
		//				+ "[Robert has been injured while hunting and is dying. Jon and the others finally take their vows to the Night's Watch. A man, sent by Robert, is captured for trying to poison Daenerys. Furious, Drogo vows to attack the Seven Kingdoms.]"
		//				+ " Episode 8: "
		//				+ "[Eddard and his men are betrayed and captured by the Lannisters. When word reaches Robb, he plans to go to war to rescue them. The White Walkers attack The Wall. Tyrion returns to his father with some new friends.]"
		//				+ " Episode 9: "
		//				+ "[Robb goes to war against the Lannisters. Jon finds himself struggling on deciding if his place is with Robb or the Night's Watch. Drogo has fallen ill from a fresh battle wound. Daenerys is desperate to save him.]"
		//				+ " Episode 10: "
		//				+ "[With Ned dead, Robb vows to get revenge on the Lannisters. Jon must officially decide if his place is with Robb or the Night's Watch. Daenerys says her final goodbye to Drogo.]"
		//				+ " Compare the provided episode dialogue against each of the spidoe summaries."
		//				+ " Identify the episode summary that best matches the events, key plot points, and character interactions described in the dialogue."
		//				+ " Provide a brief explanation of your reasoning, highlighting specific plot points or dialogue exchagnes that align with the chosen summary."
		//				+ "Output your answer in the following format: "
		//				+ " Matching Episode [Episode Number]"
		//				+ " Reasoning: [Explanation of the match, referencing key elements from the dialogue and summary]."
		//				) ) ;
//		log.info( "chatResponse: " + chatResponse.toString() ) ;


		//
		//		// Or with streaming responses
		//		final Flux< ChatResponse > fluxResponse = chatModel.stream( new Prompt( "Generate the names of 5 famous pirates." ) ) ;
		//		log.info( "fluxResponse: " + fluxResponse.toString() ) ;
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

		if( null == summary )
		{
			// No file found.
			// Generate a summary.
			log.info( "Getting summary information for episode " + episodeNum + " via chat bot...") ;
			final String promptString =  "Provide a summary of Game of Thrones Season 01 Episode " + episodeNum + " entitled " + episodeName
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

	public String getFirstMinutesOfSRTFile( final File inputFile, final int numMinutes )
	{
		assert( inputFile != null ) ;
		final long MINUTES_IN_MILLIS = numMinutes * 60 * 1000 ; // milliseconds

		StringBuilder result = new StringBuilder() ;
		try( BufferedReader reader = new BufferedReader(
				new FileReader( inputFile.getAbsolutePath() ) ) )
		{
			String line = null ;
			//			long currentTimeMillis = 0;

			while( (line = reader.readLine()) != null )
			{
				// Skip blank lines
				if( line.trim().isEmpty() )
				{
					continue ;
				}

				// SRT files are broken into blocks that each look like this:
				// 1 // the segment number
				// 00:00:12,923 --> 00:00:14,987 // start and end time for this subtitle
				// The following takes place between 5 a.m. // subtitle text

				// Check for subtitle number
				if( isInteger( line.trim() ) )
				{
					// Got the beginning of a segment
					// Read the timecode line
					final String timecodeLine = reader.readLine() ;
					if( timecodeLine != null )
					{
						final long startTimeMillis = parseTimecode( timecodeLine.split( "-->" )[ 0 ].trim() ) ;

						// If the subtitle starts within the first N minutes
						if( startTimeMillis < MINUTES_IN_MILLIS )
						{
							// Read subtitle text until a blank line
							while( (line = reader.readLine()) != null && !line.trim().isEmpty() )
							{
								result.append( line ).append(" ") ;
							}
							result.append( System.lineSeparator() ) ; // Add a newline after each subtitle
						}
						else
						{
							// If the subtitle starts after two minutes, we can stop processing
							break ; 
						} // if( startTimeMillis < 2min )
					} // if( timecodeLine != null )
				} // if( isInteger )
			} // while( line = reader )
		} // try
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
		return result.toString().trim() ;
	}

	private static boolean isInteger( final String s )
	{
		try
		{
			Integer.parseInt( s ) ;
			return true ;
		}
		catch( NumberFormatException e )
		{
			return false ;
		}
	}

	private static long parseTimecode( final String timecode )
	{
		final String[] parts = timecode.split( ":" ) ;
		final int hours = Integer.parseInt( parts[ 0 ] ) ;
		final int minutes = Integer.parseInt( parts[ 1 ] ) ;
		final String[] secondsAndMillis = parts[ 2 ].split( "," ) ;
		final int seconds = Integer.parseInt( secondsAndMillis[ 0 ] ) ;
		final int milliseconds = Integer.parseInt( secondsAndMillis[ 1 ] ) ;

		return (long) hours * 3600 * 1000 + 
				(long) minutes * 60 * 1000 + 
				(long) seconds * 1000 + 
				milliseconds ;
	}
}
