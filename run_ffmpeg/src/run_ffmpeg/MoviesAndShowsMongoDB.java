package run_ffmpeg;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import java.util.Arrays;
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
	private final String mongoDBHostName = "localhost" ;
//	private final String mongoDBHostName = "192.168.1.17" ;
	private final int mongoDBPortNumber = 27017 ;
	private final String databaseName = "MoviesAndShows" ;
	private final String probeInfoCollectionName = "probeinfo" ;
	private final String movieAndShowInfoCollectionName = "movieandshowinfos" ;
	private final String hDMoviesAndShowsCollectionName = "hdmoviesandshows" ;
	private final String sDMoviesAndShowsCollectionName = "sdmoviesandshows" ;
	private final String jobRecord_MakeFakeMKVFilesInfoCollectionName = "jobrecord_makefakemkvfiles" ;
	private final String jobRecord_TranscodeMKVFilesInfoCollectionName = "jobrecord_transcodemkvfiles" ;
	private final String jobRecord_ProbeFileInfoCollectionName = "jobrecord_probefile" ;
	private final String jobRecord_UpdateCorrelatedFileInfoCollectionName = "jobrecord_updatecorrelatedfile" ;

	/// File name to which to log activities for this application.
	static private final String logFileName = "log_movies_and_shows_mongodb.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
//	static private final String stopFileName = "C:\\Temp\\stop_database.txt" ;

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
		        .applyToClusterSettings(builder ->
		               builder.hosts( Arrays.asList( serverAddress ) ) )
		        .build() ;
		persistentMongoClient = MongoClients.create( settings ) ;
		
		// Setup the providers for passing Plain Old Java Objects (POJOs) to and from the database
		CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic( true ).build();
		CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));

		// Login to the database
//		MongoCredential credential = 
		MongoCredential.createCredential("dan", "MoviesAndShows", "BqQyH2r5xJuNu2A".toCharArray()); 
		log.fine("Connected to the database successfully" );  

		// Configure the database to use the POJO provider and retrieve the handle
		persistentDatabaseHandle = persistentMongoClient.getDatabase( databaseName ).withCodecRegistry( pojoCodecRegistry ) ;
	}

	public MongoCollection< HDorSDFile > getHDMoviesAndShowsCollection()
	{
		log.fine( "Getting HDMoviesAndShowsCollection" )  ;
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
		log.fine( "Getting SDMoviesAndShowsCollection" )  ;
		MongoCollection< HDorSDFile > theCollection = persistentDatabaseHandle.getCollection( sDMoviesAndShowsCollectionName,
				HDorSDFile.class ) ;
		return theCollection ;
	}
	
	public void dropSDMoviesAndShowCollection()
	{
		log.info( "Dropping SDMoviesAndShowsCollection" )  ;
		getSDMoviesAndShowsCollection().drop() ;
	}
	
	public MongoCollection< FFmpegProbeResult > getProbeInfoCollection()
	{	
		log.fine( "Getting probeInfoCollection" )  ;
		MongoCollection< FFmpegProbeResult > theCollection = persistentDatabaseHandle.getCollection( probeInfoCollectionName,
				FFmpegProbeResult.class ) ;
		return theCollection ;
	}

	public void dropProbeInfoCollection()
	{
		log.info( "Dropping probeInfoCollection" )  ;
		getProbeInfoCollection().drop() ;
	}
	
	public MongoCollection< MovieAndShowInfo > getMovieAndShowInfoCollection()
	{	
		log.fine( "Getting movieAndShowInfoCollectionName" )  ;
		MongoCollection< MovieAndShowInfo > theCollection = persistentDatabaseHandle.getCollection( movieAndShowInfoCollectionName,
				MovieAndShowInfo.class ) ;
		return theCollection ;
	}
	
	public void dropMovieAndShowInfoCollection()
	{
		log.info( "Dropping movieAndShowInfoCollectionName" )  ;
		getMovieAndShowInfoCollection().drop() ;
	}

	public MongoCollection< JobRecord_MakeFakeOrTranscodeMKVFile > getJobRecord_MakeFakeMKVFileInfoCollection()
	{	
		log.fine( "Getting jobRecord_MakeFakeMKVFilesCollection" )  ;
		MongoCollection< JobRecord_MakeFakeOrTranscodeMKVFile > theCollection = persistentDatabaseHandle.getCollection(
				jobRecord_MakeFakeMKVFilesInfoCollectionName,
				JobRecord_MakeFakeOrTranscodeMKVFile.class ) ;
		return theCollection ;
	}
	
	public void dropJobRecord_MakeFakeMKVFileInfoCollection()
	{
		log.info( "Dropping jobRecord_MakeFakeMKVFilesCollection" )  ;
		getJobRecord_MakeFakeMKVFileInfoCollection().drop() ;
	}
	
	public MongoCollection< JobRecord_MakeFakeOrTranscodeMKVFile > getJobRecord_TranscodeMKVFileInfoCollection()
	{	
		log.fine( "Getting jobRecord_TranscodeMKVFilesCollection" )  ;
		MongoCollection< JobRecord_MakeFakeOrTranscodeMKVFile > theCollection = persistentDatabaseHandle.getCollection(
				jobRecord_TranscodeMKVFilesInfoCollectionName,
				JobRecord_MakeFakeOrTranscodeMKVFile.class ) ;
		return theCollection ;
	}
	
	public void dropJobRecord_TranscodeMKVFileInfoCollection()
	{
		log.info( "Dropping jobRecord_TranscodeMKVFilesInfoCollection" )  ;
		getJobRecord_TranscodeMKVFileInfoCollection().drop() ;
	}
	
	public void dropJobRecord_ProbeFileInfoCollection()
	{
		log.info( "Dropping jobRecord_ProbeFileInfoCollection" )  ;
		getJobRecord_ProbeFileInfoCollection().drop() ;
	}
	
	public MongoCollection< JobRecord_ProbeFile > getJobRecord_ProbeFileInfoCollection()
	{	
		log.fine( "Getting jobRecord_ProbeFileInfoCollection" )  ;
		MongoCollection< JobRecord_ProbeFile > theCollection = persistentDatabaseHandle.getCollection(
				jobRecord_ProbeFileInfoCollectionName,
				JobRecord_ProbeFile.class ) ;
		return theCollection ;
	}
	
	public void dropJobRecord_UpdateCorrelatedFileInfoCollectionName()
	{
		log.info( "Dropping jobRecord_UpdateCorrelatedFileInfoCollection" )  ;
		getJobRecord_TranscodeMKVFileInfoCollection().drop() ;
	}
	
	public MongoCollection< JobRecord_UpdateCorrelatedFile > getJobRecord_UpdateCorrelatedFileInfoCollectionName()
	{	
		log.fine( "Getting jobRecord_UpdateCorrelatedFileInfoCollection" )  ;
		MongoCollection< JobRecord_UpdateCorrelatedFile > theCollection = persistentDatabaseHandle.getCollection(
				jobRecord_UpdateCorrelatedFileInfoCollectionName,
				JobRecord_UpdateCorrelatedFile.class ) ;
		return theCollection ;
	}
	
	/*
	      System.out.println("Credentials ::"+ credential);     
			System.out.println("Collection MoviesAndShows selected successfully");
			Document document1 = new Document("title", "MongoDB")
			.append("description", "database")
			.append("likes", 100)
			.append("url", "http://www.tutorialspoint.com/mongodb/")
			.append("by", "tutorials point");
			Document document2 = new Document("title", "RethinkDB")
			.append("description", "database")
			.append("likes", 200)
			.append("url", "http://www.tutorialspoint.com/rethinkdb/")
			.append("by", "tutorials point");
			List<Document> list = new ArrayList<Document>();
			list.add(document1);
			list.add(document2);
			collection.insertMany(list);
			// Getting the iterable object
			FindIterable<Document> iterDoc = collection.find();
			int i = 1;
			// Getting the iterator
			Iterator it = iterDoc.iterator();
			while (it.hasNext()) {
				System.out.println(it.next());
				i++;
			}
	   } 
	 */
}
