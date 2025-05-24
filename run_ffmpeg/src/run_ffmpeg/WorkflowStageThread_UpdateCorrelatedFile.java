package run_ffmpeg;

import java.util.logging.Logger;

import org.bson.conversions.Bson;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

public class WorkflowStageThread_UpdateCorrelatedFile extends WorkflowStageThread
{
	private transient MongoCollection< JobRecord_UpdateCorrelatedFile > jobRecord_UpdateCorrelatedFileInfoCollection = null ;
	private transient MongoCollection< MovieAndShowInfo > movieAndShowInfoCollection = null ;
	private MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;
	
	public WorkflowStageThread_UpdateCorrelatedFile( final String threadName,
			Logger log,
			Common common,
			MoviesAndShowsMongoDB masMDB )
	{
		super( threadName, log, common, masMDB ) ;
//		jobRecord_UpdateCorrelatedFileInfoCollection = masMDB.getJobRecord_UpdateCorrelatedFileInfoCollectionName() ;
		movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
		probeInfoCollection = masMDB.getProbeInfoCollection() ;
	}
	
	@Override
	public void doAction()
	{
		// Get the jobrecord from the database.
		// No real way to do this without deleting jobs.
		// If I don't delete the job, then this thread will infinitely spin on this one item.
//		JobRecord_UpdateCorrelatedFile theJob = jobRecord_UpdateCorrelatedFileInfoCollection.findOneAndDelete( null ) ;
//		if( null == theJob )
//		{
//			// Nothing to do.
//			return ;
//		}
//		log.info( "Got job: " + theJob ) ;
//		
//		Bson movieAndShowInfoIDdFilter = Filters.eq( "_id", theJob.getMovieAndShowInfo_id() ) ;
//		MovieAndShowInfo movieAndShowInfo = movieAndShowInfoCollection.find( movieAndShowInfoIDdFilter ).first() ;
//		if( null == movieAndShowInfo )
//		{
//			log.warning( "Unable to find movieAndShowInfo: " + theJob.getMovieAndShowInfo_id().toString() ) ;
//			return ;
//		}
//		log.fine( "Found movieAndShowInfo: " + theJob.toString() );
//		
//		Bson probeIDFilter = Filters.eq( "_id", theJob.getFfmpegProbeResult_id() ) ;
//		FFmpegProbeResult probeInfoFindResult = probeInfoCollection.find( probeIDFilter ).first() ;
//		if( null == probeInfoFindResult )
//		{
//			log.warning( "Unable to find FFmpegProbeResult: " + theJob.getFfmpegProbeResult_id() ) ;
//			return ;
//		}
//		// Post condition: movieAndShowInfo and probeInfoResult are both valid
//		
//		movieAndShowInfo.updateCorrelatedFile( probeInfoFindResult ) ;
//		movieAndShowInfo.makeReadyCorrelatedFilesList() ;
//		movieAndShowInfoCollection.replaceOne( movieAndShowInfoIDdFilter,  movieAndShowInfo ) ;
	}
}
