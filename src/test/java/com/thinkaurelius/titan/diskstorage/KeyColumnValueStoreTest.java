package com.thinkaurelius.titan.diskstorage;


import com.thinkaurelius.titan.diskstorage.keycolumnvalue.*;
import com.thinkaurelius.titan.testutil.RandomGenerator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;

import static org.junit.Assert.*;

public abstract class KeyColumnValueStoreTest {

	private Logger log = LoggerFactory.getLogger(KeyValueStoreTest.class);

	int numKeys = 500;
	int numColumns = 50;

    protected String storeName = "testStore1";
	
	public KeyColumnValueStoreManager manager;
	public StoreTransaction tx;
	public KeyColumnValueStore store;
	
	@Before
	public void setUp() throws Exception {
		openStorageManager().clearStorage();
        open();
	}

    public abstract KeyColumnValueStoreManager openStorageManager() throws StorageException;

	public void open() throws StorageException {
        manager = openStorageManager();
        tx = manager.beginTransaction(ConsistencyLevel.DEFAULT);
        store = manager.openDatabase(storeName);
    }
    
    public void clopen() throws StorageException {
        close();
        open();
    }
	
	@After
	public void tearDown() throws Exception {
		close();
	}

    public void close() throws StorageException {
        if (tx!=null) tx.commit();
        store.close();
        manager.close();
    }

    @Test
	public void createDatabase() {
		//Just setup and shutdown
	}
	
	
	public String[][] generateValues() {
		return KeyValueStoreUtil.generateData(numKeys, numColumns);
	}
	
	public void loadValues(String[][] values) throws StorageException {
		for (int i=0;i<numKeys;i++) {
			List<Entry> entries = new ArrayList<Entry>();
			for (int j=0;j<numColumns;j++) {
				entries.add(new Entry(KeyValueStoreUtil.getBuffer(j), KeyValueStoreUtil.getBuffer(values[i][j])));
			}
			store.mutate(KeyValueStoreUtil.getBuffer(i), entries, null, tx);
		}
	}
	
	public Set<KeyColumn> deleteValues(int every) throws StorageException {
		Set<KeyColumn> removed = new HashSet<KeyColumn>();
		int counter = 0;
		for (int i=0;i<numKeys;i++) {
			List<ByteBuffer> deletions = new ArrayList<ByteBuffer>();
			for (int j=0;j<numColumns;j++) {
				counter++;
				if (counter%every==0) {
					//remove
					removed.add(new KeyColumn(i,j));
					deletions.add(KeyValueStoreUtil.getBuffer(j));
				}
			}
			store.mutate(KeyValueStoreUtil.getBuffer(i), null, deletions, tx);
		}
		return removed;
	}
	
	public Set<Integer> deleteKeys(int every) throws StorageException {
		Set<Integer> removed = new HashSet<Integer>();
		for (int i=0;i<numKeys;i++) {
			if (i%every==0) {
				removed.add(i);
				List<ByteBuffer> deletions = new ArrayList<ByteBuffer>();
				for (int j=0;j<numColumns;j++) {
					deletions.add(KeyValueStoreUtil.getBuffer(j));
				}
				store.mutate(KeyValueStoreUtil.getBuffer(i), null, deletions, tx);
			}
		}
		return removed;
	}
	
	public void checkKeys(Set<Integer> removed) throws StorageException {
		for (int i=0;i<numKeys;i++) {
			if (removed.contains(i)) {
				assertFalse(store.containsKey(KeyValueStoreUtil.getBuffer(i), tx));
			} else {
				assertTrue(store.containsKey(KeyValueStoreUtil.getBuffer(i), tx));
			}
		}
	}
	
	public void checkValueExistence(String[][] values) throws StorageException {
		checkValueExistence(values,new HashSet<KeyColumn>());
	}
	
	public void checkValueExistence(String[][] values, Set<KeyColumn> removed) throws StorageException {
		for (int i=0;i<numKeys;i++) {
			for (int j=0;j<numColumns;j++) {
				boolean result = store.containsKeyColumn(KeyValueStoreUtil.getBuffer(i), KeyValueStoreUtil.getBuffer(j),tx);
				if (removed.contains(new KeyColumn(i,j))) {
					assertFalse(result);
				} else {
					assertTrue(result);
				}
			}
		}
	}
	
	public void checkValues(String[][] values) throws StorageException {
		checkValues(values,new HashSet<KeyColumn>());
	}
	
	public void checkValues(String[][] values, Set<KeyColumn> removed) throws StorageException {
		for (int i=0;i<numKeys;i++) {
			for (int j=0;j<numColumns;j++) {
				ByteBuffer result = store.get(KeyValueStoreUtil.getBuffer(i), KeyValueStoreUtil.getBuffer(j),tx);
				if (removed.contains(new KeyColumn(i,j))) {
					assertNull(result);
				} else {
					Assert.assertEquals(values[i][j], KeyValueStoreUtil.getString(result));
				}
			}
		}

	}
	
	@Test
	public void storeAndRetrieve() throws StorageException {
		String[][] values = generateValues();
		log.debug("Loading values...");
		loadValues(values);
		//print(values);
		log.debug("Checking values...");
		checkValueExistence(values);
		checkValues(values);
	}
	
	@Test
	public void storeAndRetrieveWithClosing() throws StorageException {
		String[][] values = generateValues();
		log.debug("Loading values...");
		loadValues(values);
		clopen();
		log.debug("Checking values...");
		checkValueExistence(values);
		checkValues(values);
	}
	
	@Test
	public void deleteColumnsTest1() throws StorageException {
		String[][] values = generateValues();
		log.debug("Loading values...");
		loadValues(values);
		clopen();
		Set<KeyColumn> deleted = deleteValues(7);
		log.debug("Checking values...");
		checkValueExistence(values,deleted);
		checkValues(values,deleted);
	}
	
	@Test
	public void deleteColumnsTest2() throws StorageException {
		String[][] values = generateValues();
		log.debug("Loading values...");
		loadValues(values);
		Set<KeyColumn> deleted = deleteValues(7);
		clopen();
		log.debug("Checking values...");
		checkValueExistence(values,deleted);
		checkValues(values,deleted);
	}
	
	@Test
	public void deleteKeys() throws StorageException {
		String[][] values = generateValues();
		log.debug("Loading values...");
		loadValues(values);
		Set<Integer> deleted = deleteKeys(11);
		clopen();
		checkKeys(deleted);
	}

    @Test
    public void scanTest() throws StorageException {
        if (manager.getFeatures().supportsScan()) {
            String[][] values = generateValues();
            loadValues(values);
            RecordIterator<ByteBuffer> iterator0 = store.getKeys(tx);
            assertEquals(numKeys,KeyValueStoreUtil.count(iterator0));
            clopen();
            RecordIterator<ByteBuffer> iterator1 = store.getKeys(tx);
            RecordIterator<ByteBuffer> iterator2 = store.getKeys(tx);
            RecordIterator<ByteBuffer> iterator3 = store.getKeys(tx);
            assertEquals(numKeys,KeyValueStoreUtil.count(iterator1));
            assertEquals(numKeys,KeyValueStoreUtil.count(iterator2));
        }
    }
	
	public void checkSlice(String[][] values, Set<KeyColumn> removed, int key, int start, int end, int limit) throws StorageException {
		List<Entry> entries;
		if (limit<=0)
			entries = store.getSlice(KeyValueStoreUtil.getBuffer(key), KeyValueStoreUtil.getBuffer(start), KeyValueStoreUtil.getBuffer(end), tx);
		else
			entries = store.getSlice(KeyValueStoreUtil.getBuffer(key), KeyValueStoreUtil.getBuffer(start), KeyValueStoreUtil.getBuffer(end), limit, tx);
		
		int pos=0;
		for (int i=start;i<end;i++) {
			if (removed.contains(new KeyColumn(key,i))) continue;
			if (limit<=0 || pos<limit) {
				Entry entry = entries.get(pos);
				int col = KeyValueStoreUtil.getID(entry.getColumn());
				String str = KeyValueStoreUtil.getString(entry.getValue());
				assertEquals(i,col);
				assertEquals(values[key][i],str);
			}
			pos++;
		}
        assertNotNull(entries);
		if (limit>0 && pos>limit) assertEquals(limit, entries.size());
        else assertEquals(pos,entries.size());
	}
	
	@Test
	public void intervalTest1() throws StorageException {
		String[][] values = generateValues();
		log.debug("Loading values...");
		loadValues(values);
		Set<KeyColumn> deleted = deleteValues(7);
		clopen();
		int trails = 5000;
		for (int t=0;t<trails;t++)  {
			int key = RandomGenerator.randomInt(0, numKeys);
			int start = RandomGenerator.randomInt(0, numColumns);
			int end = RandomGenerator.randomInt(start, numColumns);
			int limit = RandomGenerator.randomInt(1, 30);
			checkSlice(values,deleted,key,start,end,limit);
			checkSlice(values,deleted,key,start,end,-1);
		}

	
	}


	@Test
	public void getNonExistentKeyReturnsNull() throws Exception {
		StoreTransaction txn = manager.beginTransaction(ConsistencyLevel.DEFAULT);
		assertEquals(null, KeyColumnValueStoreUtil.get(store, txn, 0, "col0"));
		assertEquals(null, KeyColumnValueStoreUtil.get(store, txn, 0, "col1"));
		txn.commit();
	}
	
	@Test
	public void insertingGettingAndDeletingSimpleDataWorks() throws Exception {
		StoreTransaction txn = manager.beginTransaction(ConsistencyLevel.DEFAULT);
		KeyColumnValueStoreUtil.insert(store, txn, 0, "col0", "val0");
		KeyColumnValueStoreUtil.insert(store, txn, 0, "col1", "val1");
		txn.commit();
		
		txn = manager.beginTransaction(ConsistencyLevel.DEFAULT);
		assertEquals("val0", KeyColumnValueStoreUtil.get(store, txn, 0, "col0"));
		assertEquals("val1", KeyColumnValueStoreUtil.get(store, txn, 0, "col1"));
		KeyColumnValueStoreUtil.delete(store, txn, 0, "col0");
		KeyColumnValueStoreUtil.delete(store, txn, 0, "col1");
		txn.commit();
		
		txn = manager.beginTransaction(ConsistencyLevel.DEFAULT);
		assertEquals(null, KeyColumnValueStoreUtil.get(store, txn, 0, "col0"));
		assertEquals(null, KeyColumnValueStoreUtil.get(store, txn, 0, "col1"));
		txn.commit();
	}
	
//	@Test
//	public void getSliceNoLimit() throws Exception {
//		CassandraThriftStoreManager manager = new CassandraThriftStoreManager(keyspace);
//		CassandraThriftKeyColumnValueStore store =
//			manager.openDatabase(dbName);
//		
//		StoreTransaction txn = manager.beginTransaction();
//		KeyColumnValueStoreUtil.insert(store, txn, "key0", "col0", "val0");
//		KeyColumnValueStoreUtil.insert(store, txn, "key0", "col1", "val1");
//		txn.commit();
//		
//		txn = manager.beginTransaction();
//		ByteBuffer key0 = KeyColumnValueStoreUtil.stringToByteBuffer("key0");
//		ByteBuffer col0 = KeyColumnValueStoreUtil.stringToByteBuffer("col0");
//		ByteBuffer col2 = KeyColumnValueStoreUtil.stringToByteBuffer("col2");
//		List<Entry> entries = store.getSlice(key0, col0, col2, txn);
//		assertNotNull(entries);
//		assertEquals(2, entries.size());
//		assertEquals("col0", KeyColumnValueStoreUtil.byteBufferToString(entries.get(0).getColumn()));
//		assertEquals("val0", KeyColumnValueStoreUtil.byteBufferToString(entries.get(0).getValue()));
//		assertEquals("col1", KeyColumnValueStoreUtil.byteBufferToString(entries.get(1).getColumn()));
//		assertEquals("val1", KeyColumnValueStoreUtil.byteBufferToString(entries.get(1).getValue()));
//		
//		txn.commit();
//		
//		store.shutdown();
//		manager.shutdown();
//	}

	@Test
	public void getSliceRespectsColumnLimit() throws Exception {
		StoreTransaction txn = manager.beginTransaction(ConsistencyLevel.DEFAULT);
		ByteBuffer key = KeyColumnValueStoreUtil.longToByteBuffer(0);
		
		final int cols = 1024;
		
		List<Entry> entries = new LinkedList<Entry>();
		for (int i = 0; i < cols; i++) {
			ByteBuffer col = KeyColumnValueStoreUtil.longToByteBuffer(i);
			entries.add(new Entry(col, col));
		}
		store.mutate(key, entries, null, txn);
		txn.commit();
				
		txn = manager.beginTransaction(ConsistencyLevel.DEFAULT);
		ByteBuffer columnStart = KeyColumnValueStoreUtil.longToByteBuffer(0);
		ByteBuffer columnEnd = KeyColumnValueStoreUtil.longToByteBuffer(cols);
		/* 
		 * When limit is greater than or equal to the matching column count,
		 * all matching columns must be returned.
		 */
		List<Entry> result =
			store.getSlice(key, columnStart, columnEnd, cols, txn);
		assertEquals(cols, result.size());
		assertEquals(entries, result);
		result =
			store.getSlice(key, columnStart, columnEnd, cols+10, txn);
		assertEquals(cols, result.size());
		assertEquals(entries, result);

		/*
		 * When limit is less the matching column count, the columns up to the
		 * limit (ordered bytewise) must be returned.
		 */
		result = 
			store.getSlice(key, columnStart, columnEnd, cols-1, txn);
		assertEquals(cols-1, result.size());
		entries.remove(entries.size()-1);
		assertEquals(entries, result);
		result = 
			store.getSlice(key, columnStart, columnEnd, 1, txn);
		assertEquals(1, result.size());
		List<Entry> firstEntrySingleton = Arrays.asList(entries.get(0));
		assertEquals(firstEntrySingleton, result);
		txn.commit();
	}
	
	@Test
	public void getSliceRespectsAllBoundsInclusionArguments() throws Exception {
		// Test case where endColumn=startColumn+1
		ByteBuffer key = KeyColumnValueStoreUtil.longToByteBuffer(0);
		ByteBuffer columnBeforeStart = KeyColumnValueStoreUtil.longToByteBuffer(776);
		ByteBuffer columnStart = KeyColumnValueStoreUtil.longToByteBuffer(777);
		ByteBuffer columnEnd = KeyColumnValueStoreUtil.longToByteBuffer(778);
		ByteBuffer columnAfterEnd = KeyColumnValueStoreUtil.longToByteBuffer(779);
		
		// First insert four test Entries
		StoreTransaction txn = manager.beginTransaction(ConsistencyLevel.DEFAULT);
		List<Entry> entries = Arrays.asList(
				new Entry(columnBeforeStart, columnBeforeStart),
				new Entry(columnStart, columnStart),
				new Entry(columnEnd, columnEnd),
				new Entry(columnAfterEnd, columnAfterEnd));
		store.mutate(key, entries, null, txn);
		txn.commit();
		
		// getSlice() with only start inclusive
		txn = manager.beginTransaction(ConsistencyLevel.DEFAULT);
        List<Entry> result = store.getSlice(key, columnStart, columnEnd, txn);
		assertEquals(1, result.size());
		assertEquals(777, result.get(0).getColumn().getLong());
		txn.commit();

	}


    @Test
    public void containsKeyReturnsTrueOnExtantKey() throws Exception {
        ByteBuffer key1 = KeyColumnValueStoreUtil.longToByteBuffer(1);
        StoreTransaction txn = manager.beginTransaction(ConsistencyLevel.DEFAULT);
        assertFalse(store.containsKey(key1.duplicate(), txn));
        KeyColumnValueStoreUtil.insert(store, txn, 1, "c", "v");
        txn.commit();

        txn = manager.beginTransaction(ConsistencyLevel.DEFAULT);
        assertTrue(store.containsKey(key1.duplicate(), txn));
        txn.commit();
    }

	
	@Test
	public void containsKeyReturnsFalseOnNonexistentKey() throws Exception {
        StoreTransaction txn = manager.beginTransaction(ConsistencyLevel.DEFAULT);
        ByteBuffer key1 = KeyColumnValueStoreUtil.longToByteBuffer(1);
        assertFalse(store.containsKey(key1.duplicate(), txn));
        txn.commit();
	}
	

	
	@Test
	public void containsKeyColumnReturnsFalseOnNonexistentInput() throws Exception {
		StoreTransaction txn = manager.beginTransaction(ConsistencyLevel.DEFAULT);
		ByteBuffer key1 = KeyColumnValueStoreUtil.longToByteBuffer(1);
		ByteBuffer c = KeyColumnValueStoreUtil.stringToByteBuffer("c");
		assertFalse(store.containsKeyColumn(key1.duplicate(), c.duplicate(), txn));
		txn.commit();
	}
	
	@Test
	public void containsKeyColumnReturnsTrueOnExtantInput() throws Exception {
		StoreTransaction txn = manager.beginTransaction(ConsistencyLevel.DEFAULT);
		KeyColumnValueStoreUtil.insert(store, txn, 1, "c", "v");
		txn.commit();

		txn = manager.beginTransaction(ConsistencyLevel.DEFAULT);
		ByteBuffer key1 = KeyColumnValueStoreUtil.longToByteBuffer(1);
		ByteBuffer c = KeyColumnValueStoreUtil.stringToByteBuffer("c");
		assertTrue(store.containsKeyColumn(key1.duplicate(), c.duplicate(), txn));
		txn.commit();
	}
}
 