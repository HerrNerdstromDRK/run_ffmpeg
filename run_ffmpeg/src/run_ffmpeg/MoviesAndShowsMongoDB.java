package run_ffmpeg;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import java.util.logging.Logger;

import com.mongodb.MongoClient; 
import com.mongodb.MongoCredential;  

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
	
	private MongoClient persistentMongoClient = null ;
	private MongoDatabase persistentDatabaseHandle = null ;
	private final String databaseName = "MoviesAndShows" ;
	private final String probeInfoCollectionName = "probeinfo" ;
	private final String movieAndShowInfoCollectionName = "movieandshowinfos" ;
	private final String missingFileCollectionName = "missingfiles" ;
	private final String hDMoviesAndShowsCollectionName = "hdmoviesandshows" ;
	private final String sDMoviesAndShowsCollectionName = "sdmoviesandshows" ;

	/// File name to which to log activities for this application.
	static private final String logFileName = "log_movies_and_shows_mongodb.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	static private final String stopFileName = "C:\\Temp\\stop_database.txt" ;
	static private final String pathToFFPROBE = run_ffmpeg.pathToFFPROBE ;

	/// Set testMode to true to prevent mutations
	static private boolean testMode = true ;

	/**
	 * Create a new instance of this class.
	 * Setup basic configuration options and login to the database.
	 */
	public MoviesAndShowsMongoDB()
	{
		run_ffmpeg.testMode = testMode ;
		Common.setupLogger( logFileName, this.getClass().getName() ) ;
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
		persistentMongoClient = new MongoClient( "localhost" , 27017 );
		
		// Setup the providers for passing Plain Old Java Objects (POJOs) to and from the database
		CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic( true ).build();
		CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));

		// Login to the database
		MongoCredential credential = MongoCredential.createCredential("dan", "MoviesAndShows", 
				"BqQyH2r5xJuNu2A".toCharArray()); 
		System.out.println("loginToDatabase> Connected to the database successfully" );  

		// Configure the database to use the POJO provider and retrieve the handle
		persistentDatabaseHandle = persistentMongoClient.getDatabase( databaseName ).withCodecRegistry( pojoCodecRegistry ) ;
	}

	public MongoCollection< HDorSDFile > getHDMoviesAndShowsCollection()
	{
		MongoCollection< HDorSDFile > theCollection = persistentDatabaseHandle.getCollection( hDMoviesAndShowsCollectionName,
				HDorSDFile.class ) ;
		return theCollection ;
	}
	
	public void dropHDMoviesAndShowCollection()
	{
		getHDMoviesAndShowsCollection().drop() ;
	}
	
	public MongoCollection< HDorSDFile > getSDMoviesAndShowsCollection()
	{
		MongoCollection< HDorSDFile > theCollection = persistentDatabaseHandle.getCollection( sDMoviesAndShowsCollectionName,
				HDorSDFile.class ) ;
		return theCollection ;
	}
	
	public void dropSDMoviesAndShowCollection()
	{
		getSDMoviesAndShowsCollection().drop() ;
	}
	
	public MongoCollection< FFmpegProbeResult > getProbeInfoCollection()
	{	
		MongoCollection< FFmpegProbeResult > theCollection = persistentDatabaseHandle.getCollection( probeInfoCollectionName,
				FFmpegProbeResult.class ) ;
		return theCollection ;
	}

	public void dropProbeInfoCollection()
	{
		getProbeInfoCollection().drop() ;
	}
	
	public MongoCollection< MovieAndShowInfo > getMovieAndShowInfoCollection()
	{	
		MongoCollection< MovieAndShowInfo > theCollection = persistentDatabaseHandle.getCollection( movieAndShowInfoCollectionName,
				MovieAndShowInfo.class ) ;
		return theCollection ;
	}
	
	public void dropMovieAndShowInfoCollection()
	{
		getMovieAndShowInfoCollection().drop() ;
	}
	
	public MongoCollection< MissingFile > getMissingFileCollection()
	{	
		MongoCollection< MissingFile > theCollection = persistentDatabaseHandle.getCollection( missingFileCollectionName,
				MissingFile.class ) ;
		return theCollection ;
	}
	
	public void dropMissingFileCollection()
	{
		getMissingFileCollection().drop() ;
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
