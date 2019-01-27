package org.dcm4che.data;

import org.dcm4che.util.StringUtils;

import java.util.Objects;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public final class PersonName {
    public static final PersonName EMPTY = new PersonName(Group.EMPTY, Group.EMPTY, Group.EMPTY);
    public final Group alphabetic;
    public final Group ideographic;
    public final Group phonetic;

    private PersonName(Group alphabetic, Group ideographic, Group phonetic) {
        this.alphabetic = alphabetic;
        this.ideographic = ideographic;
        this.phonetic = phonetic;
    }

    private PersonName(Builder builder) {
        this(builder.alphabetic.build(), builder.ideographic.build(), builder.phonetic.build());
    }

    public static PersonName parse(String s) {
        return s != null && !s.isEmpty() ? new Builder().parse(s).build() : PersonName.EMPTY;
    }

    public final static class Group {
        public static final Group EMPTY = new Group("", "", "", "", "");
        public final String familyName;
        public final String givenName;
        public final String middleName;
        public final String namePrefix;
        public final String nameSuffix;

        private Group(String familyName, String givenName, String middleName, String namePrefix, String nameSuffix) {
            this.familyName = familyName;
            this.givenName = givenName;
            this.middleName = middleName;
            this.namePrefix = namePrefix;
            this.nameSuffix = nameSuffix;
        }

        private Group(Builder builder) {
            this(builder.familyName, builder.givenName, builder.middleName, builder.namePrefix, builder.nameSuffix);
        }

        @Override
        public String toString() {
            return nameSuffix.isEmpty()
                    ? namePrefix.isEmpty()
                    ? middleName.isEmpty()
                    ? givenName.isEmpty()
                    ? familyName
                    : familyName + '^' + givenName
                    : familyName + '^' + givenName + '^' + middleName
                    : familyName + '^' + givenName + '^' + middleName + '^' + namePrefix
                    : familyName + '^' + givenName + '^' + middleName + '^' + namePrefix + '^' + nameSuffix;
        }

        public boolean isEmpty() {
            return this == EMPTY;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Group group = (Group) o;
            return familyName.equals(group.familyName) &&
                    givenName.equals(group.givenName) &&
                    middleName.equals(group.middleName) &&
                    namePrefix.equals(group.namePrefix) &&
                    nameSuffix.equals(group.nameSuffix);
        }

        @Override
        public int hashCode() {
            return Objects.hash(familyName, givenName, middleName, namePrefix, nameSuffix);
        }

        public static final class Builder {
            private String familyName = "";
            private String givenName = "";
            private String middleName = "";
            private String namePrefix = "";
            private String nameSuffix = "";

            public Builder reset() {
                familyName = "";
                givenName = "";
                middleName = "";
                namePrefix = "";
                nameSuffix = "";
                return this;
            }

            public Builder parse(String s) {
                reset();
                if (s != null && !s.isEmpty()) {
                    String[] a = StringUtils.split(s, s.length(), '^');
                    switch (a.length) {
                        default:
                            nameSuffix = StringUtils.join(a, 4, a.length - 4, '^');
                        case 4:
                            namePrefix = a[3];
                        case 3:
                            middleName = a[2];
                        case 2:
                            givenName = a[1];
                        case 1:
                            familyName = a[0];
                    }
                }
                return this;
            }

            public Builder withFamilyName(String familyName) {
                this.familyName = familyName;
                return this;
            }

            public Builder withGivenName(String givenName) {
                this.givenName = givenName;
                return this;
            }

            public Builder withMiddleName(String middleName) {
                this.middleName = middleName;
                return this;
            }

            public Builder withNamePrefix(String namePrefix) {
                this.namePrefix = namePrefix;
                return this;
            }

            public Builder withNameSuffix(String nameSuffix) {
                this.nameSuffix = nameSuffix;
                return this;
            }

            public Group build() {
                return isEmpty() ? Group.EMPTY : new Group(this);
            }

            public boolean isEmpty() {
                return familyName.isEmpty()
                        && givenName.isEmpty()
                        && middleName.isEmpty()
                        && namePrefix.isEmpty()
                        && nameSuffix.isEmpty();
            }
        }
    }

    @Override
    public String toString() {
        String alphabeticString = alphabetic.toString();
        String ideographicString = ideographic.toString();
        String phoneticString = phonetic.toString();
        return phoneticString.isEmpty()
                ? ideographicString.isEmpty()
                ? alphabeticString
                : alphabeticString + '=' + ideographicString
                : alphabeticString + '=' + ideographicString + '=' + phoneticString;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PersonName that = (PersonName) o;
        return alphabetic.equals(that.alphabetic) &&
                ideographic.equals(that.ideographic) &&
                phonetic.equals(that.phonetic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alphabetic, ideographic, phonetic);
    }

    public static final class Builder {
        public final Group.Builder alphabetic = new Group.Builder();
        public final Group.Builder ideographic = new Group.Builder();
        public final Group.Builder phonetic = new Group.Builder();

        public Builder reset() {
            alphabetic.reset();
            ideographic.reset();
            phonetic.reset();
            return this;
        }

        public Builder parse(String s) {
            reset();
            if (s != null && !s.isEmpty()) {
                String[] a = StringUtils.split(s, s.length(), '=');
                switch (a.length) {
                    default:
                        phonetic.parse(StringUtils.join(a, 2, a.length - 2, '='));
                    case 2:
                        ideographic.parse(a[1]);
                    case 1:
                        alphabetic.parse(a[0]);
                }
            }
            return this;
        }

        public PersonName build() {
            return isEmpty() ? PersonName.EMPTY : new PersonName(this);
        }

        private boolean isEmpty() {
            return alphabetic.isEmpty() && ideographic.isEmpty() && phonetic.isEmpty();
        }
    }
}
