package dev.pan4ur.abitour.server.security

import org.mindrot.jbcrypt.BCrypt

fun hash(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt())

fun verify(password: String, stored: String): Boolean = BCrypt.checkpw(password, stored)