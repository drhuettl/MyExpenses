package org.totschnig.myexpenses.provider;

import org.totschnig.myexpenses.PaymentMethod;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class TransactionDatabase extends SQLiteOpenHelper {
  public static final String KEY_DATE = "date";
  public static final String KEY_AMOUNT = "amount";
  public static final String KEY_COMMENT = "comment";
  public static final String KEY_ROWID = "_id";
  public static final String KEY_CATID = "cat_id";
  public static final String KEY_ACCOUNTID = "account_id";
  public static final String KEY_PAYEE = "payee";
  public static final String KEY_TRANSFER_PEER = "transfer_peer";
  public static final String KEY_METHODID = "payment_method_id";
  public static final String KEY_TITLE = "title";
  public static final String KEY_LABEL_MAIN = "label_sub";
  public static final String KEY_LABEL_SUB = "label_main";

  private static final String TABLE_TRANSACTIONS = "transactions";
  private static final String TABLE_ACCOUNTS = "accounts";
  private static final String TABLE_CATEGORIES = "categories";
  private static final String TABLE_PAYMENT_METHODS = "paymentmethods";
  private static final String TABLE_ACCOUNTTYE_METHOD = "accounttype_paymentmethod";
  private static final String TABLE_TEMPLATES = "templates";
  private static final String TABLE_PAYEE = "payee";
  private static final String TABLE_FEATURE_USED = "feature_used";
  private static final int DATABASE_VERSION = 27;
  public static final String DATABASE_NAME = "data";
  private static final String TAG = "TransactionDatabase";
  /**
   * SQL statement for expenses TABLE
   * both transactions and transfers are stored in this table
   * for transfers there are two rows (one per account) which
   * are linked by transfer_peer
   * for normal transactions transfer_peer is set to NULL
   * for transfers cat_id stores the account
   */
  private static final String DATABASE_CREATE =
    "CREATE TABLE " + TABLE_TRANSACTIONS  +  "( "
    + KEY_ROWID         + " integer primary key autoincrement, "
    + KEY_COMMENT       + " text not null, "
    + KEY_DATE          + " DATETIME not null, "
    + KEY_AMOUNT        + " integer not null, "
    + KEY_CATID         + " integer, "
    + KEY_ACCOUNTID     + " integer, "
    + KEY_PAYEE         + " text, "
    + KEY_TRANSFER_PEER + " integer default 0, "
    + KEY_METHODID      + " integer);";


  /**
   * SQL statement for accounts TABLE
   */
  private static final String ACCOUNTS_CREATE =
    "CREATE TABLE " + TABLE_ACCOUNTS + " (_id integer primary key autoincrement, label text not null, " +
    "opening_balance integer, description text, currency text not null, type text default 'CASH', color integer default -3355444);";


  /**
   * SQL statement for categories TABLE
   * Table definition reflects format of Grisbis categories
   * Main categories have parent_id 0
   * usages counts how often the cat is selected
   */
  private static final String CATEGORIES_CREATE =
    "CREATE TABLE " + TABLE_CATEGORIES + " (_id integer primary key autoincrement, label text not null, " +
    "parent_id integer not null default 0, usages integer default 0, unique (label,parent_id));";

  private static final String PAYMENT_METHODS_CREATE =
      "CREATE TABLE " + TABLE_PAYMENT_METHODS + " (_id integer primary key autoincrement, label text not null, type integer default 0);";

  private static final String ACCOUNTTYE_METHOD_CREATE =
      "CREATE TABLE " + TABLE_ACCOUNTTYE_METHOD + " (type text, method_id integer, primary key (type,method_id));";

  private static final String TEMPLATE_CREATE =
      "CREATE TABLE " + TABLE_TEMPLATES + " ( "
      + KEY_ROWID         + " integer primary key autoincrement, "
      + KEY_COMMENT       + " text not null, "
      + KEY_AMOUNT        + " integer not null, "
      + KEY_CATID         + " integer, "
      + KEY_ACCOUNTID     + " integer, "
      + KEY_PAYEE         + " text, "
      + KEY_TRANSFER_PEER + " integer default 0, "
      + KEY_METHODID      + " integer, "
      + KEY_TITLE         + " text not null, "
      + "usages integer default 0, "
      + "unique(" + KEY_ACCOUNTID + "," + KEY_TITLE + "));";
  
  /**
   * we store a simple row for each time a feature has been accessed,
   * thus speeding up recording and counting 
   */
  private static final String FEATURE_USED_CREATE =
      "CREATE TABLE " + TABLE_FEATURE_USED + " (feature text not null);";

  /**
   * an SQL CASE expression for transactions
   * that gives either the category for normal transactions
   * or the account for transfers
   * full means "Main : Sub"
   */
  private static final String LABEL_MAIN =
    "CASE WHEN " +
    "  transfer_peer " +
    "THEN " +
    "  (SELECT label FROM " + TABLE_ACCOUNTS + " WHERE _id = cat_id) " +
    "WHEN " +
    "  cat_id " +
    "THEN " +
    "  CASE WHEN " +
    "    (SELECT parent_id FROM " + TABLE_CATEGORIES + " WHERE _id = cat_id) " +
    "  THEN " +
    "    (SELECT label FROM " + TABLE_CATEGORIES
        + " WHERE _id = (SELECT parent_id FROM " + TABLE_CATEGORIES
            + " WHERE _id = cat_id)) " +
    "  ELSE " +
    "    (SELECT label FROM " + TABLE_CATEGORIES + " WHERE _id = cat_id) " +
    "  END " +
    "END AS " + KEY_LABEL_MAIN;
 private static final String LABEL_SUB =
    "CASE WHEN " +
    "  NOT transfer_peer AND cat_id AND (SELECT parent_id FROM " + TABLE_CATEGORIES
        + " WHERE _id = cat_id) " +
    "THEN " +
    "  (SELECT label FROM " + TABLE_CATEGORIES + " WHERE _id = cat_id) " +
    "END AS " + KEY_LABEL_SUB;
  /**
   * same as {@link FULL_LABEL}, but if transaction is linked to a subcategory
   * only the label from the subcategory is returned
   */
  private static final String SHORT_LABEL = 
    "CASE WHEN " +
    "  transfer_peer " +
    "THEN " +
    "  (SELECT label FROM " + TABLE_ACCOUNTS + " WHERE _id = cat_id) " +
    "ELSE " +
    "  (SELECT label FROM " + TABLE_CATEGORIES + " WHERE _id = cat_id) " +
    "END AS label";

  /**
   * stores payees and payers
   */
  private static final String PAYEE_CREATE =
    "CREATE TABLE " + TABLE_PAYEE
      + " (_id integer primary key autoincrement, name text unique not null);";

  TransactionDatabase(Context context) {
    super(context, DATABASE_NAME, null, DATABASE_VERSION);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {

    db.execSQL(DATABASE_CREATE);
    db.execSQL(CATEGORIES_CREATE);
    db.execSQL(ACCOUNTS_CREATE);
    db.execSQL(PAYEE_CREATE);
    db.execSQL(PAYMENT_METHODS_CREATE);
    db.execSQL(ACCOUNTTYE_METHOD_CREATE);
    insertDefaultPaymentMethods(db);
    db.execSQL(TEMPLATE_CREATE);
    db.execSQL(FEATURE_USED_CREATE);

  }

  /**
   * @param db
   * insert the predefined payment methods in the database, all of them are valid only for bank accounts
   */
  private void insertDefaultPaymentMethods(SQLiteDatabase db) {
    ContentValues initialValues;
    long _id;
    for (PaymentMethod.PreDefined pm: PaymentMethod.PreDefined.values()) {
      initialValues = new ContentValues();
      initialValues.put("label", pm.name());
      initialValues.put("type",pm.paymentType);
      _id = db.insert(TABLE_PAYMENT_METHODS, null, initialValues);
      initialValues = new ContentValues();
      initialValues.put("method_id", _id);
      initialValues.put("type","BANK");
      db.insert(TABLE_ACCOUNTTYE_METHOD, null, initialValues);
    }
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
        + newVersion + ".");
    if (oldVersion < 17) {
      db.execSQL("drop table accounts");
      db.execSQL("CREATE TABLE accounts (_id integer primary key autoincrement, label text not null, " +
          "opening_balance integer, description text, currency text not null);");
      //db.execSQL("ALTER TABLE expenses add column account_id integer");
    }
    if (oldVersion < 18) {
      db.execSQL("CREATE TABLE payee (_id integer primary key autoincrement, name text unique not null);");
      db.execSQL("ALTER TABLE expenses add column payee text");
    }
    if (oldVersion < 19) {
      db.execSQL("ALTER TABLE expenses add column transfer_peer text");
    }
    if (oldVersion < 20) {
      db.execSQL("CREATE TABLE transactions ( _id integer primary key autoincrement, comment text not null, "
          + "date DATETIME not null, amount integer not null, cat_id integer, account_id integer, "
          + "payee  text, transfer_peer integer default null);");
      db.execSQL("INSERT INTO transactions (comment,date,amount,cat_id,account_id,payee,transfer_peer)" +
          " SELECT comment,date,CAST(ROUND(amount*100) AS INTEGER),cat_id,account_id,payee,transfer_peer FROM expenses");
      db.execSQL("DROP TABLE expenses");
      db.execSQL("ALTER TABLE accounts RENAME to accounts_old");
      db.execSQL("CREATE TABLE accounts (_id integer primary key autoincrement, label text not null, " +
          "opening_balance integer, description text, currency text not null);");
      db.execSQL("INSERT INTO accounts (label,opening_balance,description,currency)" +
          " SELECT label,CAST(ROUND(opening_balance*100) AS INTEGER),description,currency FROM accounts_old");
      db.execSQL("DROP TABLE accounts_old");
    }
    if (oldVersion < 21) {
      db.execSQL("CREATE TABLE paymentmethods (_id integer primary key autoincrement, label text not null, type integer default 0);");
      db.execSQL("CREATE TABLE accounttype_paymentmethod (type text, method_id integer, primary key (type,method_id));");
      insertDefaultPaymentMethods(db);
      db.execSQL("ALTER TABLE transactions add column payment_method_id text default 'CASH'");
      db.execSQL("ALTER TABLE accounts add column type text default 'CASH'");
    }
    if (oldVersion < 22) {
      db.execSQL("CREATE TABLE templates ( _id integer primary key autoincrement, comment text not null, "
        + "amount integer not null, cat_id integer, account_id integer, payee text, transfer_peer integer default null, "
        + "payment_method_id integer, title text not null);");
    }
    if (oldVersion < 23) {
      db.execSQL("ALTER TABLE templates RENAME to templates_old");
      db.execSQL("CREATE TABLE templates ( _id integer primary key autoincrement, comment text not null, "
          + "amount integer not null, cat_id integer, account_id integer, payee text, transfer_peer integer default null, "
          + "payment_method_id integer, title text not null, unique(account_id, title));");
      try {
        db.execSQL("INSERT INTO templates(comment,amount,cat_id,account_id,payee,transfer_peer,payment_method_id,title)" +
            " SELECT comment,amount,cat_id,account_id,payee,transfer_peer,payment_method_id,title FROM templates_old");
      } catch (SQLiteConstraintException e) {
        Log.e(TAG,e.getLocalizedMessage());
        //theoretically we could have entered duplicate titles for one account
        //we silently give up in that case (since this concerns only a narrowly distributed alpha version)
      }
      db.execSQL("DROP TABLE templates_old");
    }
    if (oldVersion < 24) {
      db.execSQL("ALTER TABLE templates add column usages integer default 0");
    }
    if (oldVersion < 25) {
      //for transactions that were not transfers, transfer_peer was set to null in transactions, but to 0 in templates
      db.execSQL("update transactions set transfer_peer=0 WHERE transfer_peer is null;");
    }
    if (oldVersion < 26) {
      db.execSQL("alter table accounts add column color integer default -6697984");
    }
    if (oldVersion < 27) {
      db.execSQL("CREATE TABLE feature_used (feature text not null);");
    }

  }
}
