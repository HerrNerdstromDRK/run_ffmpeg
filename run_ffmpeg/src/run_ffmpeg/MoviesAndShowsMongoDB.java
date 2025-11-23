package run_ffmpeg;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;

import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;  

/**
 * Class that provides instances that interact with the MoviesAndShows database.
 * This class provides basic login and interaction services, plus some larger
 * database functions, but generally does not manage the data in the database.
 * @author Dan
 */
public class MoviesAndShowsMongoDB
{
	/// Setup the logging subsystem
	private transient Logger log = null ;
	//	private transient Common common = null ;

	private com.mongodb.client.MongoClient persistentMongoClient = null ;
	private MongoDatabase persistentDatabaseHandle = null ;
	private final String mongoDBHostName = "192.168.1.132" ;
	private final int mongoDBPortNumber = 27017 ;
	private final String databaseName = "MoviesAndShows" ;
	private final String probeInfoCollectionName = "probeinfo" ;
	private final String hDMoviesAndShowsCollectionName = "hdmoviesandshows" ;
	private final String sDMoviesAndShowsCollectionName = "sdmoviesandshows" ;
	private final String action_MakeMovieChoiceName = "action_makemoviechoice" ;
	private final String action_MadeMovieChoiceName = "action_mademoviechoice" ;
	private final String action_TranscodeMKVFilesInfoCollectionName = "action_transcodemkvfiles" ;
	private final String action_CreateSRTsWithTranscribe = "action_createsrtswithtranscribe" ;
	private final String action_CreateSRTsWithOCR = "action_createsrtswithocr" ;
	private final String action_ExtractSubtitle = "action_extractsubtitle" ;

	/// File name to which to log activities for this application.
	static private final String logFileName = "log_movies_and_shows_mongodb.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.

	/**
	 * Create a new instance of this class.
	 * Setup basic configuration options and login to the database.
	 */
	public MoviesAndShowsMongoDB()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		init() ;
	}

	public MoviesAndShowsMongoDB( Logger log )
	{
		this.log = log ;
		init() ;
	}

	private void init()
	{
		java.util.logging.Logger.getLogger( "org.mongodb.driver" ).setLevel( Level.SEVERE );
		java.util.logging.Logger.getLogger( "JULLogger" ).setLevel(Level.OFF );
		//		common = new Common( log ) ;
		loginAndConfigureDatabase() ;
	}

	/**
	 * Return a handle to the logged in database.
	 * If not already logged in, do so now.
	 * @return
	 */
	public MongoDatabase getDatabase()
	{
		if( null == persistentDatabaseHandle )
		{
			loginAndConfigureDatabase() ;
		}
		return persistentDatabaseHandle ;
	}

	/**
	 * Login to the database and return the db instance.
	 * @return
	 */
	private void loginAndConfigureDatabase()
	{
		if( persistentDatabaseHandle != null )
		{
			// Already configured.
			return ;
		}
		// Creating a MongoClient and connect to the database server 
		//	      MongoClient mongo = new MongoClient( "inventory.t43ck.mongodb.net" , 8888 );
		ServerAddress serverAddress = new ServerAddress( mongoDBHostName, mongoDBPortNumber ) ;
		MongoClientSettings settings = MongoClientSettings.builder()
				.applyToClusterSettings( builder ->
				builder.hosts( Arrays.asList( serverAddress ) ) )
				.build() ;
		persistentMongoClient = MongoClients.create( settings ) ;

		// Setup the providers for passing Plain Old Java Objects (POJOs) to and from the database
		CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic( true ).build() ;
		CodecRegistry pojoCodecRegistry = fromRegistries( getDefaultCodecRegistry(), fromProviders(pojoCodecProvider) ) ;

		// Login to the database
		//		MongoCredential credential = 
		MongoCredential.createCredential("dan", "MoviesAndShows", "BqQyH2r5xJuNu2A".toCharArray()); 
		log.fine( "Connected to the database successfully" );  

		// Configure the database to use the POJO provider and retrieve the handle
		persistentDatabaseHandle = persistentMongoClient.getDatabase( databaseName ).withCodecRegistry( pojoCodecRegistry ) ;
	}

	public MongoCollection< HDorSDFile > getHDMoviesAndShowsCollection()
	{
//		log.fine( "Getting HDMoviesAndShowsCollection" )  ;
		MongoCollection< HDorSDFile > theCollection = persistentDatabaseHandle.getCollection( hDMoviesAndShowsCollectionName,
				HDorSDFile.class ) ;
		return theCollection ;
	}

	public void dropHDMoviesAndShowCollection()
	{
		log.info( "Dropping HDMoviesAndShowsCollection" )  ;
		getHDMoviesAndShowsCollection().drop() ;
	}

	public MongoCollection< HDorSDFile > getSDMoviesAndShowsCollection()
	{
//		log.fine( "Getting SDMoviesAndShowsCollection" )  ;
		MongoCollection< HDorSDFile > theCollection = persistentDatabaseHandle.getCollection( sDMoviesAndShowsCollectionName,
				HDorSDFile.class ) ;
		return theCollection ;
	}

	public void dropSDMoviesAndShowCollection()
	{
		log.info( "Dropping SDMoviesAndShowsCollection" )  ;
		getSDMoviesAndShowsCollection().drop() ;
	}

	public MongoCollection< FFmpeg_ProbeResult > getProbeInfoCollection()
	{	
//		log.fine( "Getting probeInfoCollection" )  ;
		MongoCollection< FFmpeg_ProbeResult > theCollection = persistentDatabaseHandle.getCollection( probeInfoCollectionName,
				FFmpeg_ProbeResult.class ) ;
		return theCollection ;
	}

	public void dropProbeInfoCollection()
	{
		log.info( "Dropping probeInfoCollection" )  ;
		getProbeInfoCollection().drop() ;
	}
	
	public List< FFmpeg_ProbeResult > getAllProbeInfoInstances()
	{
		List< FFmpeg_ProbeResult > allProbeInfoInstances = new ArrayList< FFmpeg_ProbeResult >() ;
		
		Bson findFilesFilter = Filters.regex( "fileNameWithPath", ".*" ) ;
		FindIterable< FFmpeg_ProbeResult > probeInfoFindResult = getProbeInfoCollection().find( findFilesFilter ) ;

		Iterator< FFmpeg_ProbeResult > probeInfoFindResultIterator = probeInfoFindResult.iterator() ;

		// This loop stores all FFmpegProbeResults in a single structure
		while( probeInfoFindResultIterator.hasNext() )
		{
			FFmpeg_ProbeResult probeResult = probeInfoFindResultIterator.next() ;
			allProbeInfoInstances.add( probeResult ) ;
		}
		return allProbeInfoInstances ;
	}

	public MongoCollection< FFmpeg_ProbeResult > getAction_TranscodeMKVFileInfoCollection()
	{	
		log.fine( "Getting action_TranscodeMKVFilesInfoCollectionName" ) ;
		MongoCollection< FFmpeg_ProbeResult > theCollection = persistentDatabaseHandle.getCollection(
				action_TranscodeMKVFilesInfoCollectionName,
				FFmpeg_ProbeResult.class ) ;
		return theCollection ;
	}

	public void dropAction_TranscodeMKVFileInfoCollection()
	{
		log.info( "Dropping action_TranscodeMKVFilesInfoCollectionName" )  ;
		getAction_TranscodeMKVFileInfoCollection().drop() ;
	}
	
	public MongoCollection< JobRecord_FileNameWithPath > getAction_CreateSRTsWithTranscribeCollection()
	{	
		log.fine( "Getting action_CreateSRTsWithTranscribe" ) ;
		MongoCollection< JobRecord_FileNameWithPath > theCollection = persistentDatabaseHandle.getCollection(
				action_CreateSRTsWithTranscribe,
				JobRecord_FileNameWithPath.class ) ;
		return theCollection ;
	}

	public void dropAction_CreateSRTsWithTranscribeCollection()
	{
		log.info( "Dropping action_CreateSRTsWithAI" )  ;
		getAction_CreateSRTsWithTranscribeCollection().drop() ;
	}
	
	public MongoCollection< JobRecord_FileNameWithPath > getAction_ExtractSubtitleCollection()
	{	
		log.fine( "Getting action_CreateSRTsWithOCR" ) ;
		MongoCollection< JobRecord_FileNameWithPath > theCollection = persistentDatabaseHandle.getCollection(
				action_ExtractSubtitle,
				JobRecord_FileNameWithPath.class ) ;
		return theCollection ;
	}

	public void dropAction_ExtractSubtitleCollection()
	{
		log.info( "Dropping action_ExtractSubtitleCollection" )  ;
		getAction_ExtractSubtitleCollection().drop() ;
	}
	
	public MongoCollection< JobRecord_FileNameWithPath > getAction_CreateSRTsWithOCRCollection()
	{	
		log.fine( "Getting action_CreateSRTsWithOCR" ) ;
		MongoCollection< JobRecord_FileNameWithPath > theCollection = persistentDatabaseHandle.getCollection(
				action_CreateSRTsWithOCR,
				JobRecord_FileNameWithPath.class ) ;
		return theCollection ;
	}

	public void dropAction_CreateSRTsWithOCRCollection()
	{
		log.info( "Dropping action_CreateSRTsWithOCR" )  ;
		getAction_CreateSRTsWithOCRCollection().drop() ;
	}
	
	public void dropAction_MakeMovieChoiceColletion()
	{
		log.info( "Dropping action_MakeMovieChoiceColletion" )  ;
		getAction_MakeMovieChoiceCollection().drop() ;
	}
	
	public MongoCollection< JobRecord_MakeMovieChoice > getAction_MakeMovieChoiceCollection()
	{	
		log.fine( "Getting action_MakeMovieChoice" ) ;
		MongoCollection< JobRecord_MakeMovieChoice > theCollection = persistentDatabaseHandle.getCollection(
				action_MakeMovieChoiceName,
				JobRecord_MakeMovieChoice.class ) ;
		return theCollection ;
	}
	
	public void dropAction_MadeMovieChoiceColletion()
	{
		log.info( "Dropping action_MadeMovieChoiceColletion" )  ;
		getAction_MadeMovieChoiceCollection().drop() ;
	}
	
	public MongoCollection< JobRecord_MadeMovieChoice > getAction_MadeMovieChoiceCollection()
	{	
		log.fine( "Getting action_MadeMovieChoice" ) ;
		MongoCollection< JobRecord_MadeMovieChoice > theCollection = persistentDatabaseHandle.getCollection(
				action_MadeMovieChoiceName,
				JobRecord_MadeMovieChoice.class ) ;
		return theCollection ;
	}

//	public void dropJobRecord_ProbeFileInfoCollection()
//	{
//		log.info( "Dropping jobRecord_ProbeFileInfoCollection" )  ;
//		getJobRecord_ProbeFileInfoCollection().drop() ;
//	}

//	public MongoCollection< JobRecord_ProbeFile > getJobRecord_ProbeFileInfoCollection()
//	{	
//		log.fine( "Getting jobRecord_ProbeFileInfoCollection" )  ;
//		MongoCollection< JobRecord_ProbeFile > theCollection = persistentDatabaseHandle.getCollection(
//				jobRecord_ProbeFileInfoCollectionName,
//				JobRecord_ProbeFile.class ) ;
//		return theCollection ;
//	}
//
//	public void dropJobRecord_UpdateCorrelatedFileInfoCollectionName()
//	{
//		log.info( "Dropping jobRecord_UpdateCorrelatedFileInfoCollection" )  ;
//		getJobRecord_TranscodeMKVFileInfoCollection().drop() ;
//	}
//
//	public MongoCollection< JobRecord_UpdateCorrelatedFile > getJobRecord_UpdateCorrelatedFileInfoCollectionName()
//	{	
//		log.fine( "Getting jobRecord_UpdateCorrelatedFileInfoCollection" )  ;
//		MongoCollection< JobRecord_UpdateCorrelatedFile > theCollection = persistentDatabaseHandle.getCollection(
//				jobRecord_UpdateCorrelatedFileInfoCollectionName,
//				JobRecord_UpdateCorrelatedFile.class ) ;
//		return theCollection ;
//	}

	/**
	 * Read all FFmpegProbeResults from the database and return them in a map keyed by the long path to the document.
	 * @return
	 */
	public Map< String, FFmpeg_ProbeResult > loadProbeInfoMap()
	{
		MongoCollection< FFmpeg_ProbeResult > probeInfoCollection = getProbeInfoCollection() ;
		Map< String, FFmpeg_ProbeResult > probeInfoMap = new HashMap< String, FFmpeg_ProbeResult >() ;

		log.fine( "Running probeInfo find..." ) ;

		// First, let's pull the info from the probeInfoCollection
		Bson findFilesFilter = Filters.regex( "fileNameWithPath", ".*" ) ;
		FindIterable< FFmpeg_ProbeResult > probeInfoFindResult = probeInfoCollection.find( findFilesFilter ) ;

		Iterator< FFmpeg_ProbeResult > probeInfoFindResultIterator = probeInfoFindResult.iterator() ;

		while( probeInfoFindResultIterator.hasNext() )
		{
			FFmpeg_ProbeResult probeResult = probeInfoFindResultIterator.next() ;
			final String pathToFile = probeResult.getFileNameWithPath() ;

			// Store the FFmpegProbeResult
			probeInfoMap.put( pathToFile, probeResult ) ;
		} // while( hasNext() )

		log.fine( "Read " + probeInfoMap.size() + " probeInfo document(s)" ) ;
		return probeInfoMap ;
	}	
}
