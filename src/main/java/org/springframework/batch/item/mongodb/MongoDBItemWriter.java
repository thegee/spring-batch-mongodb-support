package org.springframework.batch.item.mongodb;

import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.item.support.AbstractItemStreamItemWriter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;


import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.WriteConcern;

/**
 * This item writer writes documents to a MongoDB collection.
 * <p/>
 * Required parameters are:
 * <ul>
 * <li>{@link #mongo}: a reference to a {@link Mongo} connection pool.</li>
 * <li>{@link #db}: Name of the database to use. 
 * 		If the database does not exist on the MongoDB server an error occurs.</li>
 * <li>{@link #collection}: Name of the collection to read from. By default, all
 * 		documents from that collection are read.
 * 		If the collection does not exist inside the database an error occurs.</li>
 * </ul>
 * <p/>
 * Optional parameters are:
 * <ul>
 * <li>{@link #converter}</li>
 * <li>{@link #writeConcern}</li>
 * </ul>
 * 
 * @author Tobias Trelle
 */
public class MongoDBItemWriter 
	extends AbstractItemStreamItemWriter<Object> 
	implements InitializingBean, ChunkListener {
	
	private static final boolean DEFAULT_TRANSACTIONAL = true;
	
	// configurable attributes ......................................
	
	/** MongoDB connection pool. */
	protected Mongo mongo;
	
	/** Name of the database to read from. */
	protected String db;
	
	/** Name of the collection to read from. */
	protected String collection;
	
	/** Custom converter (optional). */
	protected ObjectDocumentConverter converter;
	
	/** Overwrite the write concern of the target collection (optional). */
	protected WriteConcern writeConcern;
	
	/**
	 * Flag to indicate that writing to MongoDB should be delayed if a
	 * transaction is active. Defaults to true.
	 */
	protected boolean transactional = DEFAULT_TRANSACTIONAL;
	
	private TransactionAwareMongoDBWriter transactionAwareMongoDBWriter;
	
	// public item writer interface .........................................
	
	@Override
	public void write(List<? extends Object> items) throws Exception {
		if (transactional){
			transactionAwareMongoDBWriter.writeToCollection(db, collection, writeConcern, prepareDocuments(items));
		} else {
			DBCollection coll = mongo.getDB(db).getCollection(collection);
			WriteConcern wc = writeConcern == null ? coll.getWriteConcern() : writeConcern;
			coll.insert(prepareDocuments(items), wc);
		}
	}
	
	// private methods .....................................................
	private List<DBObject> prepareDocuments(List<? extends Object> items)  {
		final List<DBObject> docs = new ArrayList<DBObject>();
		
		if ( items != null ) {
			for ( Object item: items ) {
				if ( item instanceof DBObject ) {
					docs.add( (DBObject)item);
				} else if (converter != null) {
					docs.add( converter.convert(item) );
				} else {
					throw new IllegalArgumentException("Cannot convert item to DBObject: " + item);
				}
			}
		}
		
		return docs;
	}
	
	// Setter ...............................................................
	
	public void setMongo(Mongo mongo) {
		this.mongo = mongo;
	}

	public void setDb(String db) {
		this.db = db;
	}

	public void setCollection(String collection) {
		this.collection = collection;
	}
	
	public void setConverter(ObjectDocumentConverter converter) {
		this.converter = converter;
	}

	public void setWriteConcern(WriteConcern writeConcern) {
		this.writeConcern = writeConcern;
	}
	
	/**
	 * Flag to indicate that writing to MongoDB should be delayed if a
	 * transaction is active. Defaults to true.
	 */
	public void setTransactional(boolean transactional) {
		this.transactional = transactional;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.notNull(mongo, "A Mongo instance is required");
		Assert.hasText( db, "A database name is required" );
		Assert.hasText( collection, "A collection name is required" );
		transactionAwareMongoDBWriter = new TransactionAwareMongoDBWriter(mongo);
	}

	@Override
	public void beforeChunk() {
		// Nothing to do.
	}

	@Override
	public void afterChunk() {
		if (transactional && transactionAwareMongoDBWriter.getMongoDBFailure()!=null){
			Throwable t = transactionAwareMongoDBWriter.getMongoDBFailure();
			transactionAwareMongoDBWriter.resetMongoDBFailure();
			throw new MongoDBInsertFailedException("Could not write to Mongo DB", t);
		}
	}

}
