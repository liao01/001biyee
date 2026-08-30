const appDb = db.getSiblingDB('db_chat_memory');
appDb.createUser({
  user: process.env.MONGO_APP_USERNAME,
  pwd: process.env.MONGO_APP_PASSWORD,
  roles: [{ role: 'readWrite', db: 'db_chat_memory' }]
});
