package run_ffmpeg;

import java.io.File;
import java.util.logging.Logger;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public class WorkflowStageThread_MakeFakeMKVFiles extends WorkflowStageThread
{
	private transient MongoCollection< JobRecord_MakeFakeOrTranscodeMKVFile > jobRecord_MakeFakeMKVFilesCollection = null ;
	private transient MongoCollection< JobRecord_ProbeFile > getJobRecord_ProbeFileInfoCollection = null ;
	private transient MongoCollection< MovieAndShowInfo > movieAndShowInfoCollection = null ;

	public WorkflowStageThread_MakeFakeMKVFiles( final String threadName,
			Logger log,
			Common common,
			MoviesAndShowsMongoDB masMDB )
	{
		super( threadName, log, common, masMDB ) ;
		jobRecord_MakeFakeMKVFilesCollection = masMDB.getJobRecord_MakeFakeMKVFileInfoCollection() ;
		getJobRecord_ProbeFileInfoCollection = masMDB.getJobRecord_ProbeFileInfoCollection() ;
		movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
	}

	@Override
	public void doAction()
	{
		// Get a job from the database
		//Bson mp4A = Filters.regex( "fileName", ".*" ) ;
		JobRecord_MakeFakeOrTranscodeMKVFile makeFakeJob = jobRecord_MakeFakeMKVFilesCollection.findOneAndDelete( null ) ;
		if( null == makeFakeJob )
		{
			return ;
		}
		log.info( "Got job: " + makeFakeJob.toString() ) ;
		
		Bson idFilter = Filters.eq( "_id", new ObjectId( makeFakeJob.movieOrShowName_id ) ) ;
		MovieAndShowInfo movieAndShowInfo = movieAndShowInfoCollection.find( idFilter ).first() ;
		if( null == movieAndShowInfo )
		{
			log.warning( "Unable to find movieAndShowInfo: " + makeFakeJob.movieOrShowName_id ) ;
			return ;
		}
		log.fine( "Found movieAndShowInfo: " + makeFakeJob.toString() );
		
		// the mp4LongPath *should* always be correct here since we are creating an mkv file; this implies
		// that the mp4 file is present
		// Determine if this is a movie or tv show
		boolean isMovie = true ;
		if( movieAndShowInfo.isTVShow() )
		//		if( makeFakeJob.mp4LongPath.contains( "Season " ) )
		{
			// TV Show
			isMovie = false ;
		}

		// Figure out where the MKV file should go
		String missingMKVPath = makeFakeJob.mkvLongPath ;

		if( missingMKVPath.equals( Common.getMissingFileSubstituteName() ) )
		{
			// Empty mkvLongPath -- this means that none of the MKV files attached to the movie or tv show
			// exist.
			if( isMovie )
			{
				missingMKVPath = common.getMissingMovieMKVPath()
						+ common.getPathSeparator()
						+ makeFakeJob.movieOrShowName ;
			}
			else
			{
				// TV Show
				missingMKVPath = common.getMissingTVShowMKVPath()
						+ common.getPathSeparator()
						+ movieAndShowInfo.getTVShowName()
						+ common.getPathSeparator()
						+ movieAndShowInfo.getTVShowSeasonName() ;
			}
		}
		// Post-condition: missingMKVPath should be correct.
		File missingMKVPathFile = new File( missingMKVPath ) ;
		if( !missingMKVPathFile.exists() )
		{
			log.info( "Creating directory " + missingMKVPath + " for file " + makeFakeJob.fileName ) ;
			if( !common.getTestMode() )
			{
				common.makeDirectory( missingMKVPath ) ;
			}
		}

		missingMKVPath = common.addPathSeparatorIfNecessary( missingMKVPath ) ;
		final String missingMKVFileNameWithPath = missingMKVPath
				+ makeFakeJob.fileName
				+ Common.getMissingFilePreExtension()
				+ ".mkv" ;
		log.fine( "missingMKVFileNameWithPath: " + missingMKVFileNameWithPath ) ;

		File missingMKVFileNameWithPathFile = new File( missingMKVFileNameWithPath ) ;
		if( !missingMKVFileNameWithPathFile.exists() )
		{
			// File does not already exist. Create it.
			try
			{
				if( !common.getTestMode() )
				{
					missingMKVFileNameWithPathFile.createNewFile() ;
				}
			}
			catch( Exception e )
			{
				log.warning( "Exception creating file " + missingMKVFileNameWithPath + ": " + e.toString() ) ;
				return ;
			}
		}
		else
		{
			// File already exists
			log.warning( "File already exists: " + missingMKVFileNameWithPath ) ;
			return ;
		}
		// Created file and it now exists.
		// Add a job to update the probe info so it can be collected.
		JobRecord_ProbeFile newProbeJob = new JobRecord_ProbeFile( missingMKVFileNameWithPath, movieAndShowInfo, true ) ;
		getJobRecord_ProbeFileInfoCollection.insertOne( newProbeJob ) ;
	}

}
